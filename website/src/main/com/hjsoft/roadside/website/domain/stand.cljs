(ns com.hjsoft.roadside.website.domain.stand
  (:require [com.hjsoft.roadside.website.utils :as utils]
            [clojure.string :as str]
            [com.hjsoft.roadside.common.domain.stand :as common-stand]))

(def map-home [40.0379 -76.3055])

(def default-stand-form-data
  {:name ""
   :coordinate (str (first map-home) ", " (second map-home))
   :lat (first map-home)
   :lon (second map-home)
   :address ""
   :town ""
   :state ""
   :products []
   :expiration ""
   :notes ""
   :shared? true})

(defn init-form-state [{:keys [editing-stand map-center]}]
  (let [initial (if editing-stand
                  (assoc editing-stand
                         :coordinate (str (:lat editing-stand) ", " (:lon editing-stand)))
                  (assoc default-stand-form-data
                         :coordinate (str (first map-center) ", " (second map-center))
                         :lat (first map-center)
                         :lon (second map-center)
                         :expiration (utils/in-days 28)))]
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
  (let [with-products (stand-form-reducer state [:add-product])
        [lat lon] (utils/parse-coordinates (:coordinate with-products))]
    (-> with-products
        (assoc :lat lat :lon lon)
        (dissoc :coordinate)
        common-stand/select-stand-fields)))

(def stand-key common-stand/stand-key)
(def infer-products common-stand/infer-products)

(defn add-stand
  "Processes a new stand, including product inference and duplicate check."
  [form-data stands creator]
  (let [all-unique-products (utils/get-all-unique-products stands)
        processed-data (-> form-data
                           (common-stand/prepare-common-data creator)
                           (update :products #(infer-products (:name form-data) % all-unique-products)))
        validation (common-stand/validate-stand processed-data)]
    (if (:success validation)
      (common-stand/finalize-stand stands processed-data)
      validation)))

(defn edit-stand
  "Processes an updated stand, replacing the old one in the list."
  [form-data stands editing-stand creator]
  (let [processed-data (common-stand/prepare-common-data form-data creator)
        validation (common-stand/validate-stand processed-data)]
    (if (:success validation)
      (common-stand/finalize-stand stands editing-stand processed-data)
      validation)))
