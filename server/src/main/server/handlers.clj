(ns server.handlers
  (:require [server.db :as db]
            [server.geocoding :as geo]
            [server.utils :as utils]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [ring.util.request :as rur]
            [buddy.hashers :as hashers]
            [malli.core :as m]
            [malli.error :as me]
            [taoensso.telemere :as tel]
            [com.hjsoft.roadside.common.logic :as logic]
            [com.hjsoft.roadside.common.utils :as common-utils]
            [com.hjsoft.roadside.common.domain.stand :as common-stand]))

(defn- api-response
  [code document]
  {:status code
   :headers {"Content-Type" "application/json"}
   :body (json/write-str document)})

(defn not-found [& _]
  (api-response 404 {:error "Not Found"}))

(defn ping-handler [_]
  (api-response 200 "pong"))

(defn geocode-handler [req]
  (let [address (get-in req [:params :q])]
    (if (str/blank? address)
      (api-response 400 {:error "Missing address"})
      (let [{:keys [data error]} (geo/geocode address)]
        (if error
          (api-response 502 {:error error})
          (api-response 200 data))))))

(defn reverse-geocode-handler [req]
  (let [lat (get-in req [:params :lat])
        lon (get-in req [:params :lon])]
    (if (or (str/blank? lat) (str/blank? lon))
      (api-response 400 {:error "Missing lat or lon"})
      (let [{:keys [data error]} (geo/reverse-geocode lat lon)]
        (if error
          (api-response 502 {:error error})
          (api-response 200 data))))))

(def UserSchema logic/UserSchema)

(def StandSchema logic/StandSchema)

(defn register-handler [req]
  (let [id (or (get-in req [:params :id]) (common-utils/random-uuid-str))
        login (get-in req [:params :login])
        password (get-in req [:params :password])
        email (get-in req [:params :email])
        user-data {:login login :password password :email email}]
    (if-not (m/validate UserSchema user-data)
      (api-response 400 {:status "failed"
                         :errors (me/humanize (m/explain UserSchema user-data))})
      (if (db/get-user login)
        (api-response 403 {:status "failed" :errors ["login not available"]})
        (do
          (db/save-user {:xt/id id
                         :login login
                         :password (hashers/derive password)
                         :email email})
          (api-response 201 {:login login}))))))

(def ^:const search-radius-km 500.0)

(defn get-stands-handler [req]
  (tel/log! :info {:get-stands req})
  (let [identity (:identity req)
        params (:params req)
        lat (some-> (get params :lat) Double/parseDouble)
        lon (some-> (get params :lon) Double/parseDouble)
        since (get params :since)
        stands (db/list-stands identity {:lat lat :lon lon :radius search-radius-km :since since})
        results (mapv common-stand/select-stand-fields stands)
        now (common-utils/get-current-timestamp)
        deleted-ids (if since
                      (db/list-deletions identity since {:lat lat :lon lon :radius search-radius-km})
                      [])]
    (api-response 200 {:stands results :deleted-ids deleted-ids :new-sync now})))

(defn get-stand-handler [req]
  (tel/log! :info {:get-stand req})
  (let [identity (:identity req)
        id (get-in req [:path-params :id])
        stand (db/get-stand id identity)]
    (if stand
      (api-response 200 (common-stand/select-stand-fields stand))
      (not-found))))

(defn create-stand-handler [req]
  (let [stand (-> (json/read-str (rur/body-string req) :key-fn keyword)
                  common-stand/select-stand-fields
                  (dissoc :creator))
        id (or (:id stand) (:xt/id stand) (common-utils/random-uuid-str))
        stand-to-validate (dissoc stand :id :xt/id)]
    (tel/log! :info {:create-stand stand})
    (if-not (m/validate StandSchema stand-to-validate)
      (api-response 400 {:status "failed"
                         :errors (me/humanize (m/explain StandSchema stand-to-validate))})
      (let [stand (assoc stand :xt/id id
                    :creator (:identity req))
            stand (dissoc stand :id)]
        (db/save-stand stand)
        (api-response 201 (assoc stand :id id))))))

(defn update-stand-handler [req]
  (let [id (or (get-in req [:path-params :id])
             (get-in req [:params :id]))
        stand (-> (json/read-str (rur/body-string req) :key-fn keyword)
                common-stand/select-stand-fields
                (dissoc :creator))
        existing-stand (when id (db/get-stand-unfiltered id))]
    (tel/log! :info {:update-stand stand})
    (if (and existing-stand (not= (:creator existing-stand) (:identity req)))
      (api-response 403 {:error "Forbidden: You do not own this stand"})
      (let [stand-to-validate (dissoc stand :id :xt/id)]
        (if-not (m/validate StandSchema stand-to-validate)
          (api-response 400 {:status "failed"
                             :errors (me/humanize (m/explain StandSchema stand-to-validate))})
          (let [final-id (or id (:id stand) (:xt/id stand) (common-utils/random-uuid-str))
                stand (assoc stand :xt/id final-id
                             :creator (or (:creator existing-stand) (:identity req)))
                stand (dissoc stand :id)]
            (db/save-stand stand)
            (api-response 200 (assoc stand :id final-id))))))))

(defn delete-stand-handler [req]
  (tel/log! :info {:delete-stand req})
  (let [id (get-in req [:path-params :id])
        existing-stand (db/get-stand-unfiltered id)]
    (if (and existing-stand (not= (:creator existing-stand) (:identity req)))
      (api-response 403 {:error "Forbidden: You do not own this stand"})
      (do
        (db/delete-stand id)
        (api-response 200 {:message (format "'%s' deleted" id)})))))
