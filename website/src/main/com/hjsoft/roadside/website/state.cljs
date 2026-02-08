(ns com.hjsoft.roadside.website.state
  (:require [com.hjsoft.roadside.website.storage :as storage]
            [com.hjsoft.roadside.website.utils :as utils]
            [clojure.string :as str]
            [helix.core :refer [create-context]]))

(def map-home [40.0379 -76.3055])

(def app-context (create-context))

(def initial-app-state
  {:stands (or (storage/get-item "roadside-stands") [])
   :show-form false
   :editing-stand nil
   :stand-form-data {:name ""
                     :coordinate (str (first map-home) ", " (second map-home))
                     :address ""
                     :town ""
                     :state ""
                     :products []
                     :expiration ""
                     :notes ""
                     :shared? true}
   :product-filter nil
   :selected-stand nil
   :map-center (or (storage/get-item "roadside-map-center") map-home)
   :show-settings-dialog false
   :settings-form-data {:resource "" :user "" :password ""}
   :settings (or (storage/get-item "roadside-settings") {})
   :is-synced false
   :notification nil})

(defn app-reducer [state [action-type payload]]
  (let [update-state (fn [key]
                       (if (fn? payload)
                         (update state key payload)
                         (assoc state key payload)))]
    (case action-type
      :set-stands (assoc state
                         :stands
                         (let [data (if (fn? payload)
                                      (payload (:stands state))
                                      payload)]
                           (cond
                             (vector? data) data
                             (map? data) (vec (vals data))
                             (nil? data) []
                             :else (vec data))))
      :set-show-form (update-state :show-form)
      :open-add-form (assoc state
                            :show-form true
                            :editing-stand nil
                            :stand-form-data {:name ""
                                              :coordinate (str
                                                            (first
                                                              (:map-center state))
                                                            ", "
                                                            (second
                                                              (:map-center state)))
                                              :address ""
                                              :town ""
                                              :state ""
                                              :products []
                                              :expiration (utils/in-a-week)
                                              :notes ""
                                              :shared? true})
      :open-edit-form (assoc state
                             :show-form true
                             :editing-stand payload
                             ; TODO hmm? copy for no reason?
                             :stand-form-data (assoc payload
                                                     :town (:town payload)
                                                     :state (:state payload)
                                                     :address (:address payload)
                                                     :notes (:notes payload)
                                                     :shared? (:shared? payload)))
      :close-form (assoc state :show-form false :editing-stand nil)
      :set-editing-stand (update-state :editing-stand)
      :set-stand-form-data (update-state :stand-form-data)
      :set-product-filter (update-state :product-filter)
      :set-selected-stand (update-state :selected-stand)
      :set-map-center (update-state :map-center)
      :set-show-settings-dialog (update-state :show-settings-dialog)
      :set-settings-form-data (update-state :settings-form-data)
      :set-settings (update-state :settings)
      :set-is-synced (update-state :is-synced)
      :set-notification (update-state :notification)
      state)))

(defn process-stand-form
  "Processes the stand form data, automatically adding products based on name,
   and returns a map with :success and the updated stands list or an error message."
  [form-data stands editing-stand]
  (let [all-unique-products (utils/get-all-unique-products stands)
        stand-name (:name form-data)
        updated-products (reduce
                          (fn [acc product]
                            (if (and
                                 (str/includes?
                                  (str/lower-case stand-name)
                                  (str/lower-case product))
                                 (not (some #(= % product) acc)))
                              (conj acc product)
                              acc))
                          (:products form-data)
                          all-unique-products)
        processed-data (assoc form-data
                              :products updated-products
                              :updated (utils/get-current-timestamp))]
    (if editing-stand
      {:success true
       :stands (->> stands
                 (map #(if (= % editing-stand) processed-data %))
                 vec)}
      (if (some #(= (utils/stand-key processed-data) (utils/stand-key %)) stands)
        {:success false :error "This stand already exists!"}
        {:success true :stands (conj stands processed-data)}))))

