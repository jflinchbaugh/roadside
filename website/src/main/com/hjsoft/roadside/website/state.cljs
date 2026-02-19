(ns com.hjsoft.roadside.website.state
  (:require [com.hjsoft.roadside.website.storage :as storage]
            [com.hjsoft.roadside.website.utils :as utils]
            [helix.core :refer [create-context]]
            [helix.hooks :as hooks]))

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

(def app-context (create-context))

(defn use-app []
  (let [{:keys [state
                dispatch
                user-location
                ui]} (hooks/use-context app-context)]
    {:state state
     :dispatch dispatch
     :user-location user-location
     :ui ui}))

(defn use-app-state [] (:state (use-app)))
(defn use-dispatch [] (:dispatch (use-app)))
(defn use-ui [] (:ui (use-app)))
(defn use-user-location-state [] (:user-location (use-app)))

(def initial-app-state
  {:stands (->> (or (storage/get-item "roadside-stands") [])
                (mapv (fn [s]
                        (if (:id s)
                          s
                          (assoc s :id (utils/random-uuid-str))))))
   :product-filter nil
   :selected-stand nil
   :map-center (or (storage/get-item "roadside-map-center") map-home)
   :settings (or (storage/get-item "roadside-settings") {})
   :is-synced false
   :notification nil})

(defn set-value [state key payload]
  (if (fn? payload)
    (update state key payload)
    (assoc state key payload)))

(def action-handlers
  {:set-stands (fn [state payload]
                 (assoc state
                        :stands
                        (let [data (if (fn? payload)
                                     (payload (:stands state))
                                     payload)]
                          (if (coll? data) (vec data) []))))
   :remove-stand (fn [state payload]
                   (update
                    state
                    :stands
                    (fn [stands]
                      (filterv #(not= (utils/stand-key %)
                                      (utils/stand-key payload))
                               stands))))
   :set-notification #(set-value %1 :notification %2)
   :set-is-synced #(set-value %1 :is-synced %2)
   :set-selected-stand #(set-value %1 :selected-stand %2)
   :set-product-filter #(set-value %1 :product-filter %2)
   :set-settings #(set-value %1 :settings %2)
   :set-map-center #(set-value %1 :map-center %2)})

(defn app-reducer [state [action-type payload]]
  (if-let [handler (get action-handlers action-type)]
    (handler state payload)
    state))

(defn select-filtered-stands
  [{:keys [stands product-filter]}]
  (let [sorted-stands (sort-by :updated #(compare %2 %1) stands)]
    (if product-filter
      (filterv #(some #{product-filter} (:products %)) sorted-stands)
      (vec sorted-stands))))

