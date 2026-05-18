(ns com.hjsoft.mapmarks.website.state
  (:require [com.hjsoft.mapmarks.website.storage :as storage]
            [com.hjsoft.mapmarks.website.utils :as utils]
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

(defn migrate-marks [marks]
  (->> (or marks [])
       (mapv (fn [s]
               (let [s (if (:id s)
                         s
                         (assoc s :id (utils/random-uuid-str)))]
                 (update s :tags (fn [ps] (mapv str/lower-case ps))))))))

(def initial-app-state
  {:marks (migrate-marks (storage/get-item "mapmarks-marks"))
   :tag-filter nil
   :selected-mark nil
   :map-center (or (storage/get-item "mapmarks-map-center") map-home)
   :map-zoom (or (storage/get-item "mapmarks-map-zoom") 11)
   :settings (or (storage/get-item "mapmarks-settings") {})
   :is-synced false
   :last-sync (storage/get-item "mapmarks-last-sync")
   :loading-marks? false
   :notification nil
   :show-expired? false})

(defn set-value [state key payload]
  (if (fn? payload)
    (update state key payload)
    (assoc state key payload)))

(defn- handle-set-marks [state payload]
  (let [data (if (fn? payload)
               (payload (:marks state))
               payload)
        new-data (if (coll? data) (vec data) [])
        existing-map (into {} (map (juxt :id identity) (:marks state)))
        new-map (into {} (map (juxt :id identity) new-data))
        merged-map (reduce-kv
                    (fn [m id new-mark]
                      (if-let [old-mark (get m id)]
                        (assoc m id (merge old-mark new-mark))
                        (assoc m id new-mark)))
                    existing-map
                    new-map)]
    (assoc state :marks (vec (vals merged-map)))))

(defn- handle-remove-mark [state payload]
  (update
   state
   :marks
   (fn [marks]
     (filterv #(not= (:id %) (:id payload))
              marks))))

(defn- handle-update-mark [state payload]
  (update
   state
   :marks
   (fn [marks]
     (mapv #(if (= (:id %) (:id payload)) payload %) marks))))

(defn- handle-sync-marks [state {:keys [marks deleted-ids last-sync]}]
  (let [new-marks (if (coll? marks) marks [])
        existing-map (into {} (map (juxt :id identity) (:marks state)))
        new-map (into {} (map (juxt :id identity) new-marks))
        merged-map (reduce-kv
                    (fn [m id new-mark]
                      (if-let [old-mark (get m id)]
                        (assoc m id (merge old-mark new-mark))
                        (assoc m id new-mark)))
                    existing-map
                    new-map)
        final-map (apply dissoc merged-map deleted-ids)]
    (cond-> state
      true (assoc :marks (vec (vals final-map)))
      last-sync (assoc :last-sync last-sync))))

(def action-handlers
  {:set-marks handle-set-marks
   :sync-marks handle-sync-marks
   :remove-mark handle-remove-mark
   :update-mark handle-update-mark
   :set-notification #(set-value %1 :notification %2)
   :set-is-synced #(set-value %1 :is-synced %2)
   :set-loading-marks #(set-value %1 :loading-marks? %2)
   :set-selected-mark #(set-value %1 :selected-mark %2)
   :set-tag-filter #(set-value %1 :tag-filter %2)
   :set-show-expired #(set-value %1 :show-expired? %2)
   :set-settings #(set-value %1 :settings %2)
   :set-map-center #(set-value %1 :map-center %2)
   :set-map-zoom #(set-value %1 :map-zoom %2)})

(defn app-reducer [state [action-type payload]]
  (if-let [handler (get action-handlers action-type)]
    (handler state payload)
    state))

(defn- distance-from [lat lng mark]
  (if (and (:lat mark) (:lon mark))
    (utils/haversine-distance lat lng (:lat mark) (:lon mark))
    js/Number.MAX_VALUE))

(defn select-marks-by-expiry
  [{:keys [marks show-expired?]} & [user-location]]
  (let [filtered (if show-expired?
                   marks
                   (filterv #(not (utils/past-expiration? (:expiration %))) marks))]
    (if (and user-location (seq user-location))
      (let [[u-lat u-lng] user-location]
        (sort-by (partial distance-from u-lat u-lng) filtered))
      (sort-by :updated #(compare %2 %1) filtered))))
