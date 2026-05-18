(ns server.db
  (:require [xtdb.api :as xt]
            [tick.core :as t]
            [taoensso.telemere :as tel]
            [malli.core :as m]
            [malli.error :as me]
            [clojure.string :as str]
            [com.hjsoft.mapmarks.common.logic :as logic]))

(defonce node (atom nil))

(defn get-user [username]
  (first
   (xt/q @node
         ['(fn [u]
             (-> (from :users [{:login u}
                               login password email enabled?])))
          username])))

(defn get-mark-unfiltered [id]
  (first
   (xt/q @node
         ['(fn [id-param]
             (from :marks
                   [{:xt/id id-param}
                    xt/id
                    creator
                    name
                    address
                    town
                    state
                    tags
                    expiration
                    notes
                    shared?
                    updated
                    lat
                    lon]))
          id])))

(defn get-mark [id-param user-id]
  (first
   (xt/q @node
         ['(fn [id-val u]
             (-> (unify
                  (from :marks [{:xt/id sid}
                                 xt/id
                                 creator
                                 name
                                 address
                                 town
                                 state
                                 tags
                                 expiration
                                 notes
                                 shared?
                                 updated
                                 lat
                                 lon])
                  (left-join (from :votes [{:mark-id vsid}
                                           value
                                           {:user-id vuser}])
                             [value vuser] (= sid vsid))
                  (left-join (from :users [{:login login}
                                           {:enabled? enabled?}])
                             [enabled?] (= login creator)))
                 (where (and (or (nil? enabled?) enabled?)
                             (= sid id-val)
                             (or (= creator u)
                                 (= shared? true))))
                 (aggregate xt/id
                            creator
                            name
                            address
                            town
                            state
                            tags
                            expiration
                            notes
                            shared?
                            updated
                            lat
                            lon
                            {:score (sum (if (nil? value) 0 value))
                             :user-vote (sum
                                         (if (and (not (nil? vuser)) (= vuser u))
                                             value 0))})))
          id-param user-id])))

(defn list-marks
  ([user-id] (list-marks user-id nil))
  ([user-id {:keys [lat lon radius]}]
   (let [q (if (and lat lon radius)
             (let [rad (/ Math/PI 180.0)
                   lat1-rad (* lat rad)
                   lon1-rad (* lon rad)
                   R 6371.0]
               ['(fn [u lat1-rad lon1-rad rad R r]
                   (->
                    (unify
                     (from :marks
                           [{:xt/id sid}
                            xt/id
                            creator
                            name
                            address
                            town
                            state
                            tags
                            expiration
                            notes
                            shared?
                            updated
                            lat
                            lon])
                     (left-join (from :users [{:login login}
                                              {:enabled? enabled?}])
                                [enabled?]
                                (= login creator)))
                    (where (and
                            (or (nil? enabled?) enabled?)
                            (or (= creator u) (= shared? true))
                            (<= (* R (* 2.0 (asin (sqrt (+ (* (sin (/ (- (* lat rad) lat1-rad) 2.0))
                                                              (sin (/ (- (* lat rad) lat1-rad) 2.0)))
                                                           (* (cos lat1-rad) (cos (* lat rad))
                                                              (sin (/ (- (* lon rad) lon1-rad) 2.0))
                                                              (sin (/ (- (* lon rad) lon1-rad) 2.0))))))))
                                r)))
                    (aggregate sid
                               xt/id
                               creator
                               name
                               address
                               town
                               state
                               tags
                               expiration
                               notes
                               shared?
                               updated
                               lat
                               lon)
                    (with {:user-id u})
                    (with {:score (pull
                                   (fn [sid]
                                     (->
                                      (from :votes
                                            [{:mark-id sid}
                                             mark-id
                                             value])
                                      (aggregate mark-id
                                                 {:value (sum value)})
                                      (return value))))
                           :user-vote (pull
                                       (fn [sid user-id]
                                         (->
                                          (from :votes
                                                [{:mark-id sid}
                                                 {:user-id user-id}
                                                 mark-id
                                                 user-id
                                                 value])
                                          (aggregate mark-id user-id
                                                     {:value (sum value)})
                                          (return value))))})
                    (with {:score (coalesce (. score value) 0)})
                    (with {:user-vote (coalesce (. user-vote value) 0)})))
                user-id lat1-rad lon1-rad rad R radius])
             ['(fn [u]
                 (->
                  (unify
                   (from :marks
                         [{:xt/id sid}
                          xt/id
                          creator
                          name
                          address
                          town
                          state
                          tags
                          expiration
                          notes
                          shared?
                          updated
                          lat
                          lon])
                   (left-join (from :users [{:login login}
                                            {:enabled? enabled?}])
                              [enabled?]
                              (= login creator)))
                  (where (and
                          (or (nil? enabled?) enabled?)
                          (or (= creator u) (= shared? true))))
                  (aggregate sid
                             xt/id
                             creator
                             name
                             address
                             town
                             state
                             tags
                             expiration
                             notes
                             shared?
                             updated
                             lat
                             lon)
                  (with {:user-id u})
                  (with {:score (pull
                                 (fn [sid]
                                   (->
                                    (from :votes
                                          [{:mark-id sid}
                                           mark-id
                                           value])
                                    (aggregate mark-id
                                               {:value (sum value)})
                                    (return value))))
                         :user-vote (pull
                                     (fn [sid user-id]
                                       (->
                                        (from :votes
                                              [{:mark-id sid}
                                               {:user-id user-id}
                                               mark-id
                                               user-id
                                               value])
                                        (aggregate mark-id user-id
                                                   {:value (sum value)})
                                        (return value))))})
                  (with {:score (coalesce (. score value) 0)})
                  (with {:user-vote (coalesce (. user-vote value) 0)})))
              user-id])]
     (tel/log! :info {:list-marks q})
     (vec (xt/q @node q)))))

(defn list-deletions
  [user-id since {:keys [lat lon radius]}]
  (if-not (and since lat lon radius)
    []
    (let [rad (/ Math/PI 180.0)
          lat1-rad (* lat rad)
          lon1-rad (* lon rad)
          R 6371.0
          since-inst (t/instant since)
          ;; Find all versions that ended (were deleted or updated) since 'since'
          ;; and were in the requested radius.
          q ['(fn [u s lat1-rad lon1-rad rad R r]
                (-> (from :marks {:for-valid-time :all-time, :bind [xt/id lat lon xt/valid-to creator shared?]})
                    (where (and (>= xt/valid-to s)
                                (or (= creator u) (= shared? true))
                                (<= (* R (* 2.0 (asin (sqrt (+ (* (sin (/ (- (* lat rad) lat1-rad) 2.0))
                                                                  (sin (/ (- (* lat rad) lat1-rad) 2.0)))
                                                               (* (cos lat1-rad) (cos (* lat rad))
                                                                  (sin (/ (- (* lon rad) lon1-rad) 2.0))
                                                                  (sin (/ (- (* lon rad) lon1-rad) 2.0))))))))
                                    r)))))
             user-id since-inst lat1-rad lon1-rad rad R radius]

          ended (xt/q @node q)
          ;; Find what is currently active
          active-ids (set (map :xt/id (list-marks user-id {:lat lat :lon lon :radius radius})))]
      (vec (set (keep #(when-not (contains? active-ids (:xt/id %)) (:xt/id %)) ended))))))

(defn migrate-marks! []
  (let [marks (xt/q @node '(from :marks [xt/id
                                           creator
                                           name
                                           address
                                           town
                                           state
                                           tags
                                           expiration
                                           notes
                                           shared?
                                           updated
                                           coordinate
                                           ]))
        ops (keep (fn [mark]
                    (let [needs-coord-migration (and (:coordinate mark) (not (and (:lat mark) (:lon mark))))
                          tags (:tags mark)
                          needs-tag-migration (some #(not= % (str/lower-case %)) tags)]
                      (when (or needs-coord-migration needs-tag-migration)
                        (tel/log! :info {:migrating-mark (:xt/id mark)})
                        (let [updated-mark (cond-> mark
                                              needs-coord-migration (as-> s (if-let [[lat lon] (logic/parse-coordinate (:coordinate s))]
                                                                              (-> s (assoc :lat lat :lon lon) (dissoc :coordinate))
                                                                              (do (tel/log! :error {:migration-failed (:xt/id s) :msg "Could not parse coordinate"}) s)))
                                              needs-tag-migration (update :tags #(mapv str/lower-case %)))]
                          [:put-docs :marks updated-mark]))))
                  marks)]
    (when (seq ops)
      (xt/execute-tx @node (vec ops)))))

(defn save-user [user]
  (xt/execute-tx @node [[:put-docs :users (assoc user :updated (str (t/now)))]]))

(defn save-mark [mark]
  (let [mark (-> mark
                  (assoc :updated (str (t/now)))
                  (update :tags #(mapv str/lower-case %)))]
    (if-not (m/validate logic/MarkSchema (dissoc mark :xt/id))
      (throw (ex-info "Invalid mark data"
                      {:errors (me/humanize (m/explain logic/MarkSchema (dissoc mark :xt/id)))}))
      (xt/execute-tx @node [[:put-docs :marks mark]]))))

(defn delete-mark [id]
  (xt/execute-tx @node [[:delete-docs :marks id]]))

(defn vote-mark [mark-id user-id value]
  (let [vote-id (str mark-id "-" user-id)]
    (if (zero? value)
      (xt/execute-tx @node [[:delete-docs :votes vote-id]])
      (xt/execute-tx @node [[:put-docs :votes {:xt/id vote-id :mark-id mark-id :user-id user-id :value value}]]))))
