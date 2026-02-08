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

(defn app-reducer [state [action-type payload]]
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
    :open-add-form (assoc state
                          :show-form true
                          :editing-stand nil)
    :open-edit-form (assoc state
                           :show-form true
                           :editing-stand payload)
    :close-form (assoc state :show-form false :editing-stand nil)
    :remove-stand (update state :stands (fn [stands]
                                          (filterv #(not= % payload) stands)))
    ;; Generic handler for :set-* actions
    (let [action-name (name action-type)]
      (if (str/starts-with? action-name "set-")
        (let [key (keyword (subs action-name 4))]
          (if (fn? payload)
            (update state key payload)
            (assoc state key payload)))
        state))))

(defn process-stand-form
  "Processes the stand form data, automatically adding products based on name,
   and returns a map with :success and the updated stands list or an error message."
  [form-data stands editing-stand]
  (let [all-unique-products (utils/get-all-unique-products stands)
        stand-name (str/lower-case (:name form-data))
        current-products (set (:products form-data))
        inferred-products (filter
                           (fn [p]
                             (and (str/includes? stand-name (str/lower-case p))
                                  (not (contains? current-products p))))
                           all-unique-products)
        final-products (into (:products form-data) inferred-products)
        processed-data (assoc form-data
                              :products (vec final-products)
                              :updated (utils/get-current-timestamp))]
    (if editing-stand
      {:success true
       :processed-data processed-data
       :stands (mapv #(if (= % editing-stand) processed-data %) stands)}
      (if (some #(= (utils/stand-key processed-data) (utils/stand-key %)) stands)
        {:success false :error "This stand already exists!"}
        {:success true
         :processed-data processed-data
         :stands (conj stands processed-data)}))))

