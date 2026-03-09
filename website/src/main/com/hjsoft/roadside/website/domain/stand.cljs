(ns com.hjsoft.roadside.website.domain.stand
  (:require [com.hjsoft.roadside.website.utils :as utils]
            [clojure.string :as str]))

(def map-home [40.0379 -76.3055])

(def default-stand-form-data
  {:name ""
   :coordinate (str (first map-home) ", " (second map-home))
   :address ""
   :town ""
   :state ""
   :products []
   :expiration ""
   :notes ""
   :shared? true})

(defn init-form-state [{:keys [editing-stand map-center]}]
  (let [initial (or editing-stand
                    (assoc default-stand-form-data
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
    :add-product (let [product (str/trim (or (:current-product state) ""))]
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
  (stand-form-reducer state [:add-product]))

(defn stand-key
  "Generates a unique key for a stand, preferring ID but falling back to content."
  [stand]
  (if-let [id (:id stand)]
    (str id)
    (let [{:keys [name coordinate address town state products]} stand]
      (str name "|" coordinate "|" address "|" town "|" state "|" (str/join "," products)))))

(defn infer-products
  "Automatically detects and adds products that appear in the stand name
   if they already exist in other stands."
  [stand-name current-products all-products]
  (let [name-lower (str/lower-case (or stand-name ""))
        current-set (set current-products)]
    (->> all-products
         (filter (fn [p]
                   (and (str/includes? name-lower (str/lower-case p))
                        (not (contains? current-set p)))))
         (into (or current-products []))
         (vec))))

(defn- ensure-id [stand]
  (if (:id stand)
    stand
    (assoc stand :id (utils/random-uuid-str))))

(defn- ensure-creator [stand creator]
  (if (empty? (str (:creator stand)))
    (assoc stand :creator creator)
    stand))

(defn- prepare-common-data
  "Ensures stand has an ID, creator, and updated timestamp."
  [form-data creator]
  (-> form-data
      ensure-id
      (ensure-creator creator)
      (assoc :updated (utils/get-current-timestamp))))

(defn add-stand
  "Processes a new stand, including product inference and duplicate check."
  [form-data stands creator]
  (let [all-unique-products (utils/get-all-unique-products stands)
        final-products (infer-products (:name form-data)
                                       (:products form-data)
                                       all-unique-products)
        processed-data (assoc (prepare-common-data form-data creator)
                              :products final-products)]
    (if (some #(= (stand-key processed-data) (stand-key %)) stands)
      {:success false :error "This stand already exists!"}
      {:success true
       :processed-data processed-data
       :stands (conj (vec stands) processed-data)})))

(defn edit-stand
  "Processes an updated stand, replacing the old one in the list."
  [form-data stands editing-stand creator]
  (let [processed-data (prepare-common-data form-data creator)]
    {:success true
     :processed-data processed-data
     :stands (mapv (fn [s]
                     (if (= (stand-key s) (stand-key editing-stand))
                       processed-data
                       s))
                   stands)}))
