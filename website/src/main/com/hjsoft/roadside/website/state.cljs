(ns com.hjsoft.roadside.website.state
  (:require [com.hjsoft.roadside.website.storage :as storage]
            [com.hjsoft.roadside.website.utils :as utils]
            [clojure.string :as str]
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

(defn migrate-stands [stands]
  (->> (or stands [])
       (mapv (fn [s]
               (let [s (if (:id s)
                         s
                         (assoc s :id (utils/random-uuid-str)))]
                 (update s :products (fn [ps] (mapv str/lower-case ps))))))))

(def initial-app-state
  {:stands (migrate-stands (storage/get-item "roadside-stands"))
   :product-filter nil
   :selected-stand nil
   :map-center (or (storage/get-item "roadside-map-center") map-home)
   :map-zoom (or (storage/get-item "roadside-map-zoom") 11)
   :settings (or (storage/get-item "roadside-settings") {})
   :is-synced false
   :last-sync (storage/get-item "roadside-last-sync")
   :loading-stands? false
   :notification nil
   :show-expired? false})

(defn set-value [state key payload]
  (if (fn? payload)
    (update state key payload)
    (assoc state key payload)))

(defn- handle-set-stands [state payload]
  (let [data (if (fn? payload)
               (payload (:stands state))
               payload)
        new-data (if (coll? data) (vec data) [])
        existing-map (into {} (map (juxt :id identity) (:stands state)))
        new-map (into {} (map (juxt :id identity) new-data))
        merged-map (reduce-kv
                    (fn [m id new-stand]
                      (if-let [old-stand (get m id)]
                        (assoc m id (merge old-stand new-stand))
                        (assoc m id new-stand)))
                    existing-map
                    new-map)]
    (assoc state :stands (vec (vals merged-map)))))

(defn- handle-remove-stand [state payload]
  (update
   state
   :stands
   (fn [stands]
     (filterv #(not= (:id %) (:id payload))
              stands))))

(defn- handle-update-stand [state payload]
  (update
   state
   :stands
   (fn [stands]
     (mapv #(if (= (:id %) (:id payload)) payload %) stands))))

(defn- handle-sync-stands [state {:keys [stands deleted-ids last-sync]}]
  (let [new-stands (if (coll? stands) stands [])
        existing-map (into {} (map (juxt :id identity) (:stands state)))
        new-map (into {} (map (juxt :id identity) new-stands))
        merged-map (reduce-kv
                    (fn [m id new-stand]
                      (if-let [old-stand (get m id)]
                        (assoc m id (merge old-stand new-stand))
                        (assoc m id new-stand)))
                    existing-map
                    new-map)
        final-map (apply dissoc merged-map deleted-ids)]
    (cond-> state
      true (assoc :stands (vec (vals final-map)))
      last-sync (assoc :last-sync last-sync))))

(def action-handlers
  {:set-stands handle-set-stands
   :sync-stands handle-sync-stands
   :remove-stand handle-remove-stand
   :update-stand handle-update-stand
   :set-notification #(set-value %1 :notification %2)
   :set-is-synced #(set-value %1 :is-synced %2)
   :set-loading-stands #(set-value %1 :loading-stands? %2)
   :set-selected-stand #(set-value %1 :selected-stand %2)
   :set-product-filter #(set-value %1 :product-filter %2)
   :set-show-expired #(set-value %1 :show-expired? %2)
   :set-settings #(set-value %1 :settings %2)
   :set-map-center #(set-value %1 :map-center %2)
   :set-map-zoom #(set-value %1 :map-zoom %2)})

(defn app-reducer [state [action-type payload]]
  (if-let [handler (get action-handlers action-type)]
    (handler state payload)
    state))

(defn- distance-from [lat lng stand]
  (if (and (:lat stand) (:lon stand))
    (utils/haversine-distance lat lng (:lat stand) (:lon stand))
    js/Number.MAX_VALUE))

(defn select-stands-by-expiry
  [{:keys [stands show-expired?]} & [user-location]]
  (let [filtered (if show-expired?
                   stands
                   (filterv #(not (utils/past-expiration? (:expiration %))) stands))]
    (if (and user-location (seq user-location))
      (let [[u-lat u-lng] user-location]
        (sort-by (partial distance-from u-lat u-lng) filtered))
      (sort-by :updated #(compare %2 %1) filtered))))
