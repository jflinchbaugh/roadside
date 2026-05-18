(ns com.hjsoft.mapmarks.common.domain.mark
  (:require [com.hjsoft.mapmarks.common.utils :as utils]
            [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]
            [com.hjsoft.mapmarks.common.logic :as logic]))

(defn- get-map-keys [schema]
  (->> (m/entries schema)
       (map first)
       set))

(def mark-keys (get-map-keys logic/MarkSchema))

(defn select-mark-fields
  "Returns a map containing only the keys defined in MarkSchema (plus :xt/id, :score, :user-vote)."
  [mark]
  (let [valid-keys (conj mark-keys :xt/id :score :user-vote)]
    (select-keys mark valid-keys)))

(defn mark-key
  "Generates a unique key for a mark, preferring ID but falling back to content."
  [mark]
  (when mark
    (if-let [id (or (:id mark) (:xt/id mark))]
      (str id)
      (let [{:keys [name lat lon address town state tags]} mark]
        (str name "|" lat "," lon "|" address "|" town "|" state "|" (str/join "," tags))))))

(defn- tag-matches-name? [name-lower current-set tag]
  (and (str/includes? name-lower tag)
       (not (contains? current-set tag))))

(defn infer-tags
  "Automatically detects and adds tags that appear in the mark name
   if they already exist in other marks."
  [mark-name current-tags all-tags]
  (let [name-lower (str/lower-case (or mark-name ""))
        current-set (set (map str/lower-case current-tags))]
    (->> all-tags
         (filter #(tag-matches-name? name-lower current-set %))
         (into (or current-tags []))
         (map str/lower-case)
         (vec))))

(defn- ensure-id [mark]
  (if (or (:id mark) (:xt/id mark))
    mark
    (assoc mark :id (utils/random-uuid-str))))

(defn- ensure-creator [mark creator]
  (if (empty? (str (:creator mark)))
    (assoc mark :creator creator)
    mark))

(defn prepare-common-data
  "Ensures mark has an ID, creator, and updated timestamp."
  [form-data creator]
  (-> form-data
      ensure-id
      (ensure-creator creator)
      (assoc :updated (utils/get-current-timestamp))))

(defn- same-mark? [s1 s2]
  (= (mark-key s1) (mark-key s2)))

(defn- replace-matching-mark [marks old-mark new-mark]
  (mapv #(if (same-mark? % old-mark) new-mark %) marks))

(defn finalize-mark
  "Standardized result for mark operations.
   Two arities:
   - [marks processed-data] -> Add (with duplicate check)
   - [marks editing-mark processed-data] -> Edit (replace matching)"
  ([marks processed-data]
   (if (some #(same-mark? processed-data %) marks)
     {:success false :error "This mark already exists!"}
     (finalize-mark marks nil processed-data)))
  ([marks editing-mark processed-data]
   {:success true
    :processed-data processed-data
    :marks (if editing-mark
              (replace-matching-mark marks editing-mark processed-data)
              (conj (vec marks) processed-data))}))

(defn validate-mark [mark]
  (if-not (m/validate logic/MarkSchema (dissoc mark :xt/id))
    (let [errors (me/humanize (m/explain logic/MarkSchema (dissoc mark :xt/id)))]
      {:success false :error (str "Validation failed: " (str/join ", " (map (fn [[k v]] (str (name k) ": " (str/join "; " v))) errors)))})
    {:success true}))
