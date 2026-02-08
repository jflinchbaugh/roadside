(ns com.hjsoft.roadside.website.state
  (:require [com.hjsoft.roadside.website.storage :as storage]
            [com.hjsoft.roadside.website.utils :as utils]
            [clojure.string :as str]
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
  (let [{:keys [state dispatch user-location]} (hooks/use-context app-context)]
    {:state state
     :dispatch dispatch
     :user-location user-location}))

(def initial-app-state
  {:stands (or (storage/get-item "roadside-stands") [])
   :show-form false
   :editing-stand nil
   :product-filter nil
   :selected-stand nil
   :map-center (or (storage/get-item "roadside-map-center") map-home)
   :show-settings-dialog false
   :settings (or (storage/get-item "roadside-settings") {})
   :is-synced false
   :notification nil})

(defn set-value [state key payload]
  (if (fn? payload)
    (update state key payload)
    (assoc state key payload)))

(defn app-reducer [state [action-type payload]]
  (case action-type
    :set-stands (assoc state
                       :stands
                       (let [data (if (fn? payload)
                                    (payload (:stands state))
                                    payload)]
                         (if (coll? data) (vec data) [])))
    :open-add-form (assoc state
                          :show-form true
                          :editing-stand nil)
    :open-edit-form (assoc state
                           :show-form true
                           :editing-stand payload)
    :close-form (assoc state :show-form false :editing-stand nil)
    :remove-stand (update state :stands (fn [stands]
                                          (filterv #(not= % payload) stands)))
    ;; Explicit handlers
    :set-notification (set-value state :notification payload)
    :set-is-synced (set-value state :is-synced payload)
    :set-selected-stand (set-value state :selected-stand payload)
    :set-product-filter (set-value state :product-filter payload)
    :set-show-settings-dialog (set-value state :show-settings-dialog payload)
    :set-settings (set-value state :settings payload)
    :set-map-center (set-value state :map-center payload)
    state))

(defn select-filtered-stands
  [{:keys [stands product-filter]}]
  (let [sorted-stands (sort-by :updated #(compare %2 %1) stands)]
    (if product-filter
      (filterv #(some #{product-filter} (:products %)) sorted-stands)
      (vec sorted-stands))))

