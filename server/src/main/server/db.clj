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
             (-> (from :users [login password email])
                 (where (= login u))))
          username])))

(defn get-stand-unfiltered [id]
  (first
   (xt/q @node
         ['(fn [id-param]
             (-> (from :stands [xt/id *])
                 (where (= xt/id id-param))))
          id])))

(defn get-stand [id user-id]
  (first
   (xt/q @node
         ['(fn [id-param u]
             (-> (from :stands [xt/id *])
                 (where (and (= xt/id id-param)
                             (or (= creator u)
                                 (= shared? true))))))
          id user-id])))

(defn list-stands
  ([user-id] (list-stands user-id nil))
  ([user-id {:keys [lat lon radius]}]
   (let [q (if (and lat lon radius)
             (let [rad (/ Math/PI 180.0)
                   lat1-rad (* lat rad)
                   lon1-rad (* lon rad)
                   R 6371.0]
               ['(fn [u lat1-rad lon1-rad rad R r]
                   (-> (from :stands [lat lon creator shared? *])
                       (where (and (or (= creator u)
                                       (= shared? true))
                                   (<= (* R (* 2.0 (asin (sqrt (+ (* (sin (/ (- (* lat rad) lat1-rad) 2.0))
                                                                     (sin (/ (- (* lat rad) lat1-rad) 2.0)))
                                                                  (* (cos lat1-rad) (cos (* lat rad))
                                                                     (sin (/ (- (* lon rad) lon1-rad) 2.0))
                                                                     (sin (/ (- (* lon rad) lon1-rad) 2.0))))))))
                                       r)))))
                user-id lat1-rad lon1-rad rad R radius])
             ['(fn [u]
                 (-> (from :stands [creator shared? *])
                     (where (or (= creator u)
                                (= shared? true)))))
              user-id])]
     (tel/log! :info {:list-stands q})
     (vec (xt/q @node q)))))

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
