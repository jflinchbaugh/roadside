(ns com.hjsoft.mapmarks.website.domain.mark
  (:require [com.hjsoft.mapmarks.website.utils :as utils]
            [clojure.string :as str]
            [com.hjsoft.mapmarks.common.domain.mark :as common-mark]))

(def map-home [40.0379 -76.3055])

(def default-mark-form-data
  {:name ""
   :coordinate (str (first map-home) ", " (second map-home))
   :lat (first map-home)
   :lon (second map-home)
   :address ""
   :town ""
   :state ""
   :tags []
   :expiration ""
   :notes ""
   :shared? true})

(defn init-form-state [{:keys [editing-mark map-center default-expiration-days]}]
  (let [initial (if editing-mark
                  (assoc editing-mark
                         :coordinate (str (:lat editing-mark) ", " (:lon editing-mark)))
                  (assoc default-mark-form-data
                         :coordinate (str (first map-center) ", " (second map-center))
                         :lat (first map-center)
                         :lon (second map-center)
                         :expiration (if (and default-expiration-days
                                              (pos? default-expiration-days))
                                       (utils/in-days default-expiration-days)
                                       "")))]
    (assoc initial
           :show-address? (boolean (or (seq (:address initial))
                                       (seq (:town initial))
                                       (seq (:state initial))))
           :current-tag ""
           :user-modified-coordinate? (boolean editing-mark))))

(defn- remove-tag-by-name [tags tag-name]
  (filterv #(not= % tag-name) tags))

(defn mark-form-reducer [state [action-type payload]]
  (case action-type
    :update-field (let [[field value] payload]
                    (cond-> (assoc state field value)
                      (= field :coordinate) (assoc :user-modified-coordinate? true)))
    :sync-coordinate (if (:user-modified-coordinate? state)
                       state
                       (assoc state
                              :coordinate payload
                              :lat (first (utils/parse-coordinates payload))
                              :lon (second (utils/parse-coordinates payload))))
    :update-current-tag (assoc state :current-tag payload)
    :add-tag (let [tag (str/trim (str/lower-case (or (:current-tag state) "")))]
                   (if (or (empty? tag)
                           (some #(= % tag) (:tags state)))
                     (assoc state :current-tag "")
                     (-> state
                         (update :tags #(conj (or % []) tag))
                         (assoc :current-tag ""))))
    :remove-tag (update state :tags #(remove-tag-by-name % payload))
    :toggle-address (update state :show-address? not)
    state))

(defn prepare-submit-data [state]
  (let [with-pending (mark-form-reducer state [:add-tag])
        [lat lon] (utils/parse-coordinates (:coordinate with-pending))
        ;; Final normalization of all tags
        normalized-tags (utils/get-all-unique-tags [{:tags (:tags with-pending)}])]
    (-> with-pending
        (assoc :lat lat :lon lon :tags normalized-tags)
        (dissoc :coordinate)
        common-mark/select-mark-fields)))

(def mark-key common-mark/mark-key)
(def infer-tags common-mark/infer-tags)

(defn add-mark
  "Processes a new mark, including tag inference and duplicate check."
  [form-data marks creator]
  (let [all-unique-tags (utils/get-all-unique-tags marks)
        processed-data (-> form-data
                           (common-mark/prepare-common-data creator)
                           (update :tags #(infer-tags (:name form-data) % all-unique-tags)))
        validation (common-mark/validate-mark processed-data)]
    (if (:success validation)
      (common-mark/finalize-mark marks processed-data)
      validation)))

(defn edit-mark
  "Processes an updated mark, replacing the old one in the list."
  [form-data marks editing-mark creator]
  (let [processed-data (common-mark/prepare-common-data form-data creator)
        validation (common-mark/validate-mark processed-data)]
    (if (:success validation)
      (common-mark/finalize-mark marks editing-mark processed-data)
      validation)))
