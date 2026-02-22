(ns com.hjsoft.roadside.website.domain.stand
  (:require [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.state :as state]
            [clojure.string :as str]))

(defn init-form-state [{:keys [editing-stand map-center]}]
  (let [initial (or editing-stand
                    (assoc state/default-stand-form-data
                           :coordinate (str (first map-center) ", " (second map-center))
                           :expiration (utils/in-a-week)))]
    (assoc initial
           :show-address? (boolean (or (seq (:address initial))
                                       (seq (:town initial))
                                       (seq (:state initial))))
           :current-product "")))

(defn stand-form-reducer [state [action-type payload]]
  (case action-type
    :update-field (assoc state (first payload) (second payload))
    :update-current-product (assoc state :current-product payload)
    :add-product (let [product (str/trim (or payload (:current-product state) ""))]
                   (if (or (empty? product)
                           (some #(= % product) (:products state)))
                     (assoc state :current-product "")
                     (-> state
                         (update :products #(conj (or % []) product))
                         (assoc :current-product ""))))
    :remove-product (update
                     state
                     :products
                     (fn [products]
                       (filterv #(not= % payload) products)))
    :toggle-address (update state :show-address? not)
    state))

(defn prepare-submit-data [state]
  (let [state-with-product (if (not-empty (:current-product state))
                             (stand-form-reducer state [:add-product])
                             state)]
    (dissoc state-with-product :current-product :show-address?)))

(defn process-stand-form
  "Processes the stand form data, automatically adding products based on name,
   and returns a map with :success and the updated stands list or an error message."
  [form-data stands editing-stand creator]
  (let [all-unique-products (utils/get-all-unique-products stands)
        stand-name (str/lower-case (:name form-data))
        current-products (set (:products form-data))
        inferred-products (filter
                           (fn [p]
                             (and (str/includes? stand-name (str/lower-case p))
                                  (not (contains? current-products p))))
                           all-unique-products)
        final-products (into (:products form-data) inferred-products)
        processed-data (assoc (if (:id form-data)
                                form-data
                                (assoc form-data
                                       :id (utils/random-uuid-str)
                                       :creator creator))
                              :products (vec final-products)
                              :updated (utils/get-current-timestamp))]
    (if editing-stand
      {:success true
       :processed-data processed-data
       :stands (mapv (fn [s]
                       (if (= (utils/stand-key s) (utils/stand-key editing-stand))
                         processed-data
                         s))
                     stands)}
      (if (some #(= (utils/stand-key processed-data) (utils/stand-key %)) stands)
        {:success false :error "This stand already exists!"}
        {:success true
         :processed-data processed-data
         :stands (conj stands processed-data)}))))
