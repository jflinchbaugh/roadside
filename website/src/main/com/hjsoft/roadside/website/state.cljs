(ns com.hjsoft.roadside.website.state
  (:require [com.hjsoft.roadside.website.storage :as storage]
            [com.hjsoft.roadside.website.utils :as utils]))

(def map-home [40.0379 -76.3055])

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
      :set-stands (update-state :stands)
      :set-show-form (update-state :show-form)
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
