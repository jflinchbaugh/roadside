(ns com.hjsoft.roadside.website.state
  (:require [com.hjsoft.roadside.website.storage :as storage]
            [com.hjsoft.roadside.website.utils :as utils]
            [helix.core :refer [create-context]]
            [helix.hooks :as hooks]))

(def map-home [40.0379 -76.3055])

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
   :notification nil
   :show-expired? false})

(defn set-value [state key payload]
  (if (fn? payload)
    (update state key payload)
    (assoc state key payload)))

(def action-handlers
  {:set-stands (fn [state payload]
                 (let [data (if (fn? payload)
                              (payload (:stands state))
                              payload)
                       new-data (if (coll? data) (vec data) [])
                       existing-map (into {} (map (juxt :id identity) (:stands state)))
                       new-map (into {} (map (juxt :id identity) new-data))
                       merged-map (merge existing-map new-map)]
                   (assoc state :stands (vec (vals merged-map)))))
   :remove-stand (fn [state payload]
                   (update
                    state
                    :stands
                    (fn [stands]
                      (filterv #(not= (:id %) (:id payload))
                               stands))))
   :set-notification #(set-value %1 :notification %2)
   :set-is-synced #(set-value %1 :is-synced %2)
   :set-selected-stand #(set-value %1 :selected-stand %2)
   :set-product-filter #(set-value %1 :product-filter %2)
   :set-show-expired #(set-value %1 :show-expired? %2)
   :set-settings #(set-value %1 :settings %2)
   :set-map-center #(set-value %1 :map-center %2)})

(defn app-reducer [state [action-type payload]]
  (if-let [handler (get action-handlers action-type)]
    (handler state payload)
    state))

(defn select-stands-by-expiry
  [{:keys [stands show-expired?]} & [user-location]]
  (let [filtered (if show-expired?
                   stands
                   (filterv #(not (utils/past-expiration? (:expiration %))) stands))]
    (if (and user-location (seq user-location))
      (let [[u-lat u-lng] user-location]
        (sort-by
         (fn [stand]
           (if-let [[s-lat s-lon] (utils/parse-coordinates (:coordinate stand))]
             (utils/haversine-distance u-lat u-lng s-lat s-lon)
             js/Number.MAX_VALUE))
         filtered))
      (sort-by :updated #(compare %2 %1) filtered))))

(defn select-filtered-stands
  [{:keys [product-filter] :as state}]
  (let [filtered-by-expiry (select-stands-by-expiry state)]
    (if product-filter
      (filterv #(some #{product-filter} (:products %)) filtered-by-expiry)
      (vec filtered-by-expiry))))
