(ns server.handlers
  (:require [server.db :as db]
            [server.geocoding :as geo]
            [server.utils :as utils]
            [server.config :as config]
            [clojure.data.json :as json]
            [clojure.data.csv :as csv]
            [clojure.string :as str]
            [ring.util.request :as rur]
            [buddy.hashers :as hashers]
            [malli.core :as m]
            [malli.error :as me]
            [taoensso.telemere :as tel]
            [hiccup2.core :as h]
            [com.hjsoft.roadside.common.logic :as logic]
            [com.hjsoft.roadside.common.utils :as common-utils]
            [com.hjsoft.roadside.common.domain.stand :as common-stand]))

(defn- api-response
  [code document]
  {:status code
   :headers {"Content-Type" "application/json"}
   :body (json/write-str document)})

(defn- stands->csv [stands]
  (let [header ["Name" "Latitude" "Longitude" "Address" "Town" "State" "Products" "Notes"]
        rows (map (fn [{:keys [name lat lon address products notes town state]}]
                    [name
                     (str lat)
                     (str lon)
                     address
                     town
                     state
                     (str/join "; " products)
                     notes])
                  stands)]
    (with-out-str
      (csv/write-csv *out* (into [header] rows)))))

(defn get-stands-csv-handler [req]
  (let [identity (:identity req)
        stands (db/list-stands identity)
        csv (stands->csv stands)]
    {:status 200
     :headers {"Content-Type" "text/csv"
               "Content-Disposition" "attachment; filename=\"stands.csv\""}
     :body csv}))

(defn- stand->placemark [stand]
  (let [{:keys [name lat lon address products notes]} stand]
    [:Placemark
     [:name (or name "Roadside Stand")]
     [:description (str "Address: " address "\n"
                        "Products: " (str/join ", " products) "\n"
                        "Notes: " (or notes ""))]
     [:Point
      [:coordinates (format "%f,%f,0" lon lat)]]]))

(defn- stands->kml [stands]
  (str (h/html
        {:mode :xml}
        (h/raw "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        [:kml {:xmlns "http://www.opengis.net/kml/2.2"}
         [:Document
          [:name "Roadside Stands"]
          (map stand->placemark stands)]])))

(defn- format-rfc822 [iso-str]
  (try
    (let [inst (java.time.Instant/parse iso-str)
          zdt (java.time.ZonedDateTime/ofInstant inst (java.time.ZoneId/of "UTC"))
          formatter java.time.format.DateTimeFormatter/RFC_1123_DATE_TIME]
      (.format formatter zdt))
    (catch Exception _
      nil)))

(defn- stand->rss-item [base-url stand]
  (let [{:keys [name address town state products expiration notes updated xt/id lat lon shared? creator]} stand
        full-address (str/join ", " (remove str/blank? [address town state]))
        description (str/join "\n"
                              (remove nil?
                                      [(when (seq full-address) (str "Address: " full-address))
                                       (when (seq products) (str "Products: " (str/join ", " products)))
                                       (when (seq expiration) (str "Expires: " expiration))
                                       (when (seq notes) (str "Notes: " notes))
                                       (str "Coordinates: " lat ", " lon)
                                       (when (some? shared?) (str "Shared: " (if shared? "Yes" "No")))
                                       (when (seq creator) (str "Creator: " creator))]))]
    [:item
     [:title (or name "Roadside Stand")]
     [:link (str base-url "#stand=" id)]
     [:description description]
     (when-let [pub-date (format-rfc822 updated)]
       [:pubDate pub-date])
     [:guid {:isPermaLink "false"} id]]))

(defn- stands->rss [stands base-url]
  (str (h/html
        {:mode :xml}
        (h/raw "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        [:rss {:version "2.0"
               :xmlns:atom "http://www.w3.org/2005/Atom"}
         [:channel
          [:title "Roadside Stands"]
          [:link base-url]
          [:description "Latest roadside stands"]
          [:atom:link {:href (str base-url "api/stands.rss") :rel "self" :type "application/rss+xml"}]
          (map (partial stand->rss-item base-url) stands)]])))

(defn get-stands-rss-handler [req]
  (let [identity (:identity req)
        stands (db/list-stands identity)
        base-url config/external-base-url
        rss (stands->rss stands base-url)]
    {:status 200
     :headers {"Content-Type" "application/rss+xml"
               "Content-Disposition" "inline"}
     :body rss}))

(defn get-stands-kml-handler [req]
  (let [identity (:identity req)
        stands (db/list-stands identity)
        kml (stands->kml stands)]
    {:status 200
     :headers {"Content-Type" "application/vnd.google-earth.kml+xml"
               "Content-Disposition" "attachment; filename=\"stands.kml\""}
     :body kml}))

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
        user-data {:login login :password password :email email :enabled? true}]
    (if-not (m/validate UserSchema user-data)
      (api-response 400 {:status "failed"
                         :errors (me/humanize (m/explain UserSchema user-data))})
      (if (db/get-user login)
        (api-response 403 {:status "failed" :errors {:login ["not available"]}})
        (do
          (db/save-user (assoc
                          user-data
                          :xt/id id
                          :password (hashers/derive password)))
          (api-response 201 {:login login}))))))

 (defn get-stands-handler
  [req]
  (tel/log! :info {:get-stands req})
  (let [identity (:identity req)
        params (:params req)
        lat (some-> (get params :lat) Double/parseDouble)
        lon (some-> (get params :lon) Double/parseDouble)
        since (get params :since)
        stands (db/list-stands identity {:lat lat
                                         :lon lon
                                         :radius logic/search-radius-km})
        results (mapv common-stand/select-stand-fields stands)
        now (common-utils/get-current-timestamp)
        deleted-ids (if since
                      (db/list-deletions identity since {:lat lat :lon lon :radius logic/search-radius-km})
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
      (let [stand (assoc
                    stand
                    :xt/id id
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
          (let [final-id (or id (:id stand) (:xt/id stand)
                           (common-utils/random-uuid-str))
                stand (assoc
                        stand
                        :xt/id final-id
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

(defn vote-stand-handler [req]
  (tel/log! :info {:vote-stand req})
  (let [id (get-in req [:path-params :id])
        identity (:identity req)
        body (json/read-str (rur/body-string req) :key-fn keyword)
        value (:value body)]
    (if (not identity)
      (api-response 401 {:error "Unauthorized"})
      (if (not (contains? #{1 -1 0} value))
        (api-response 400 {:error "Invalid vote value"})
        (do
          (db/vote-stand id identity value)
          (api-response 200 {:status "success"}))))))
