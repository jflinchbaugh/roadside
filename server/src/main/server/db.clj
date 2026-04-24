(ns server.db
  (:require [xtdb.api :as xt]
            [tick.core :as t]
            [taoensso.telemere :as tel]
            [malli.core :as m]
            [malli.error :as me]
            [com.hjsoft.roadside.common.logic :as logic]
            [com.hjsoft.roadside.common.domain.stand :as common-stand]))

(defonce node (atom nil))

(defn get-user [username]
  (first
   (xt/q @node
         ['(fn [u]
             (-> (from :users [login password email enabled?])
                 (where (= login u))))
          username])))

(defn get-stand-unfiltered [id]
  (first
   (xt/q @node
         ['(fn [id-param]
             (-> (from :stands [xt/id *])
                 (where (= xt/id id-param))))
          id])))

(defn get-stand [id-param user-id]
  (first
   (xt/q @node
         ['(fn [id-val u]
             (-> (unify
                  (from :stands [{:xt/id id} creator name address town state products expiration notes shared? updated lat lon])
                  (left-join (from :votes [{:stand-id id} value {:user-id vote-user}]) [value vote-user]))
                 (where (and (= id id-val)
                             (or (= creator u)
                                 (= shared? true))))
                 (with {:v (if (= vote-user u) value 0)})
                 (aggregate id creator name address town state products expiration notes shared? updated lat lon
                            {:score (sum value)
                             :user-vote (sum v)})))
          id-param user-id])))

(defn list-stands
  ([user-id] (list-stands user-id nil))
  ([user-id {:keys [lat lon radius]}]
   (let [q (if (and lat lon radius)
             (let [rad (/ Math/PI 180.0)
                   lat1-rad (* lat rad)
                   lon1-rad (* lon rad)
                   R 6371.0]
               ['(fn [u lat1-rad lon1-rad rad R r]
                   (-> (unify
                        (from :stands [{:xt/id id} creator name address town state products expiration notes shared? updated lat lon])
                        (left-join (from :votes [{:stand-id id} value {:user-id vote-user}]) [value vote-user]))
                       (where (and (or (= creator u)
                                       (= shared? true))
                                   (<= (* R (* 2.0 (asin (sqrt (+ (* (sin (/ (- (* lat rad) lat1-rad) 2.0))
                                                                     (sin (/ (- (* lat rad) lat1-rad) 2.0)))
                                                                  (* (cos lat1-rad) (cos (* lat rad))
                                                                     (sin (/ (- (* lon rad) lon1-rad) 2.0))
                                                                     (sin (/ (- (* lon rad) lon1-rad) 2.0))))))))
                                       r)))
                       (with {:v (if (= vote-user u) value 0)})
                       (aggregate id creator name address town state products expiration notes shared? updated lat lon
                                  {:score (sum value)
                                   :user-vote (sum v)})))
                user-id lat1-rad lon1-rad rad R radius])
             ['(fn [u]
                 (-> (unify
                      (from :stands [{:xt/id id} creator name address town state products expiration notes shared? updated lat lon])
                      (left-join (from :votes [{:stand-id id} value {:user-id vote-user}]) [value vote-user]))
                     (where (or (= creator u)
                                (= shared? true)))
                     (with {:v (if (= vote-user u) value 0)})
                     (aggregate id creator name address town state products expiration notes shared? updated lat lon
                                {:score (sum value)
                                 :user-vote (sum v)})
                     (order-by {:val updated :dir :desc})))
              user-id])]
     (tel/log! :info {:list-stands q})
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
                (-> (from :stands {:for-valid-time :all-time, :bind [xt/id lat lon xt/valid-to creator shared?]})
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
          active-ids (set (map :xt/id (list-stands user-id {:lat lat :lon lon :radius radius})))]
      (vec (set (keep #(when-not (contains? active-ids (:xt/id %)) (:xt/id %)) ended))))))

(defn migrate-stands! []
  (let [stands (xt/q @node '(from :stands [xt/id *]))]
    (doseq [stand stands]
      (when (and (:coordinate stand) (not (and (:lat stand) (:lon stand))))
        (tel/log! :info {:migrating-stand (:xt/id stand)})
        (if-let [[lat lon] (logic/parse-coordinate (:coordinate stand))]
          (let [updated-stand (-> stand
                                  (assoc :lat lat :lon lon)
                                  (dissoc :coordinate))]
            (xt/submit-tx @node [[:put-docs :stands updated-stand]]))
          (tel/log! :error {:migration-failed (:xt/id stand) :msg "Could not parse coordinate"}))))))

(defn save-user [user]
  (xt/submit-tx @node [[:put-docs :users (assoc user :updated (str (t/now)))]]))

(defn save-stand [stand]
  (let [stand (assoc stand :updated (str (t/now)))]
    (if-not (m/validate logic/StandSchema (dissoc stand :xt/id))
      (throw (ex-info "Invalid stand data"
                      {:errors (me/humanize (m/explain logic/StandSchema (dissoc stand :xt/id)))}))
      (xt/submit-tx @node [[:put-docs :stands stand]]))))

(defn delete-stand [id]
  (xt/submit-tx @node [[:delete-docs :stands id]]))

(defn vote-stand [stand-id user-id value]
  (let [vote-id (str stand-id "-" user-id)]
    (if (zero? value)
      (xt/submit-tx @node [[:delete-docs :votes vote-id]])
      (xt/submit-tx @node [[:put-docs :votes {:xt/id vote-id :stand-id stand-id :user-id user-id :value value}]]))))
