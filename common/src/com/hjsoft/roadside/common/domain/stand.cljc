(ns com.hjsoft.roadside.common.domain.stand
  (:require [com.hjsoft.roadside.common.utils :as utils]
            [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]
            [com.hjsoft.roadside.common.logic :as logic]))

(defn- get-map-keys [schema]
  (->> (m/entries schema)
       (map first)
       set))

(def stand-keys (get-map-keys logic/StandSchema))

(defn select-stand-fields
  "Returns a map containing only the keys defined in StandSchema (plus :xt/id)."
  [stand]
  (let [valid-keys (conj stand-keys :xt/id)]
    (select-keys stand valid-keys)))

(defn stand-key
  "Generates a unique key for a stand, preferring ID but falling back to content."
  [stand]
  (when stand
    (if-let [id (or (:id stand) (:xt/id stand))]
      (str id)
      (let [{:keys [name lat lon address town state products]} stand]
        (str name "|" lat "," lon "|" address "|" town "|" state "|" (str/join "," products))))))

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
  (if (or (:id stand) (:xt/id stand))
    stand
    (assoc stand :id (utils/random-uuid-str))))

(defn- ensure-creator [stand creator]
  (if (empty? (str (:creator stand)))
    (assoc stand :creator creator)
    stand))

(defn prepare-common-data
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

(defn finalize-stand
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

(defn validate-stand [stand]
  (if-not (m/validate logic/StandSchema (dissoc stand :xt/id))
    (let [errors (me/humanize (m/explain logic/StandSchema (dissoc stand :xt/id)))]
      {:success false :error (str "Validation failed: " (str/join ", " (map (fn [[k v]] (str (name k) ": " (str/join "; " v))) errors)))})
    {:success true}))
