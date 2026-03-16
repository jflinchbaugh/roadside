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
            [taoensso.telemere :as tel]))

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

(def UserSchema
  [:map
   [:login [:re #"^[a-zA-Z0-9_]{3,20}$"]]
   [:password [:string {:min 8}]]
   [:email [:re #".+@.+\..+"]]
   [:updated {:optional true} [:maybe :string]]])
(def StandSchema
  [:map
   [:name [:string {:min 1}]]
   [:coordinate {:optional true} [:re #"^-?\d+\.?\d*,\s*-?\d+\.?\d*$"]]
   [:location {:optional true} [:maybe :string]]
   [:address {:optional true} [:maybe :string]]
   [:town {:optional true} [:maybe :string]]
   [:state {:optional true} [:maybe :string]]
   [:products {:optional true} [:vector :string]]
   [:expiration {:optional true} [:maybe :string]]
   [:notes {:optional true} [:maybe :string]]
   [:shared? {:optional true} :boolean]
   [:updated {:optional true} [:maybe :string]]])

(defn register-handler [req]
  (let [id (or (get-in req [:params :id]) (str (java.util.UUID/randomUUID)))
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
  (let [params (:params req)
        lat (some-> (get params :lat) Double/parseDouble)
        lon (some-> (get params :lon) Double/parseDouble)
        stands (db/list-stands)]
    (if (and lat lon)
      (let [filtered (filterv
                      (fn [stand]
                        (if-let [[s-lat s-lon] (utils/parse-coordinate (:coordinate stand))]
                          (<= (utils/haversine-distance lat lon s-lat s-lon) search-radius-km)
                          false))
                      stands)]
        (api-response 200 filtered))
      (api-response 200 stands))))

(defn get-stand-handler [req]
  (let [id (get-in req [:path-params :id])
        stand (db/get-stand id)]
    (if stand
      (api-response 200 stand)
      (not-found))))

(def transient-fields [:show-address? :current-product :editing-stand :map-center])

(defn- sanitize-stand [stand]
  (apply dissoc stand transient-fields))

(defn create-stand-handler [req]
  (let [stand (-> (json/read-str (rur/body-string req) :key-fn keyword)
                  sanitize-stand
                  (dissoc :creator))
        id (or (:id stand) (:xt/id stand) (str (java.util.UUID/randomUUID)))
        stand-to-validate (dissoc stand :id :xt/id)]
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
                  sanitize-stand
                  (dissoc :creator))
        existing-stand (when id (db/get-stand id))]
    (if (and existing-stand (not= (:creator existing-stand) (:identity req)))
      (api-response 403 {:error "Forbidden: You do not own this stand"})
      (let [stand-to-validate (dissoc stand :id :xt/id)]
        (if-not (m/validate StandSchema stand-to-validate)
          (api-response 400 {:status "failed"
                             :errors (me/humanize (m/explain StandSchema stand-to-validate))})
          (let [final-id (or id (:id stand) (:xt/id stand) (str (java.util.UUID/randomUUID)))
                stand (assoc stand :xt/id final-id
                             :creator (or (:creator existing-stand) (:identity req)))
                stand (dissoc stand :id)]
            (db/save-stand stand)
            (api-response 200 (assoc stand :id final-id))))))))

(defn delete-stand-handler [req]
  (let [id (get-in req [:path-params :id])
        existing-stand (db/get-stand id)]
    (if (and existing-stand (not= (:creator existing-stand) (:identity req)))
      (api-response 403 {:error "Forbidden: You do not own this stand"})
      (do
        (db/delete-stand id)
        (api-response 200 {:message (format "'%s' deleted" id)})))))
