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

(defn- remove-product-by-name [products product-name]
  (filterv #(not= % product-name) products))

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
    :remove-product (update state :products #(remove-product-by-name % payload))
    :toggle-address (update state :show-address? not)
    state))

(defn prepare-submit-data [state]
  (-> state
      (stand-form-reducer [:add-product])
      (dissoc :show-address? :current-product :editing-stand :map-center)))

(defn stand-key
  "Generates a unique key for a stand, preferring ID but falling back to content."
  [stand]
  (if-let [id (:id stand)]
    (str id)
    (let [{:keys [name coordinate address town state products]} stand]
      (str name "|" coordinate "|" address "|" town "|" state "|" (str/join "," products)))))

(defn- product-matches-name? [name-lower current-set product]
  (and (str/includes? name-lower (str/lower-case product))
       (not (contains? current-set product))))

(defn infer-products
  "Automatically detects and adds products that appear in the stand name
   if they already exist in other stands."
  [stand-name current-products all-products]
  (let [name-lower (str/lower-case (or stand-name ""))
        current-set (set current-products)]
    (->> all-products
         (filter #(product-matches-name? name-lower current-set %))
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

(defn- same-stand? [s1 s2]
  (= (stand-key s1) (stand-key s2)))

(defn- replace-matching-stand [stands old-stand new-stand]
  (mapv #(if (same-stand? % old-stand) new-stand %) stands))

(defn- finalize-stand
  "Standardized result for stand operations.
   Two arities:
   - [stands processed-data] -> Add (with duplicate check)
   - [stands editing-stand processed-data] -> Edit (replace matching)"
  ([stands processed-data]
   (if (some #(same-stand? processed-data %) stands)
     {:success false :error "This stand already exists!"}
     (finalize-stand stands nil processed-data)))
  ([stands editing-stand processed-data]
   {:success true
    :processed-data processed-data
    :stands (if editing-stand
              (replace-matching-stand stands editing-stand processed-data)
              (conj (vec stands) processed-data))}))

(defn add-stand
  "Processes a new stand, including product inference and duplicate check."
  [form-data stands creator]
  (let [all-unique-products (utils/get-all-unique-products stands)]
    (-> form-data
        (prepare-common-data creator)
        (update :products #(infer-products (:name form-data) % all-unique-products))
        (->> (finalize-stand stands)))))

(defn edit-stand
  "Processes an updated stand, replacing the old one in the list."
  [form-data stands editing-stand creator]
  (-> form-data
      (prepare-common-data creator)
      (->> (finalize-stand stands editing-stand))))
