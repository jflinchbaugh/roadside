(ns server.handlers
  (:require [server.db :as db]
            [server.geocoding :as geo]
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
            [com.hjsoft.mapmarks.common.logic :as logic]
            [com.hjsoft.mapmarks.common.utils :as common-utils]
            [com.hjsoft.mapmarks.common.domain.mark :as common-mark]))

(defn- api-response
  [code document]
  {:status code
   :headers {"Content-Type" "application/json"}
   :body (json/write-str document)})

(defn- marks->csv [marks]
  (let [header ["Name" "Latitude" "Longitude" "Address" "Town" "State" "Tags" "Notes"]
        rows (map (fn [{:keys [name lat lon address tags notes town state]}]
                    [name
                     (str lat)
                     (str lon)
                     address
                     town
                     state
                     (str/join "; " tags)
                     notes])
                  marks)]
    (with-out-str
      (csv/write-csv *out* (into [header] rows)))))

(defn get-marks-csv-handler [req]
  (let [identity (:identity req)
        marks (db/list-marks identity)
        csv (marks->csv marks)]
    {:status 200
     :headers {"Content-Type" "text/csv"
               "Content-Disposition" "attachment; filename=\"marks.csv\""}
     :body csv}))

(defn- mark->placemark [mark]
  (let [{:keys [name lat lon address tags notes]} mark]
    [:Placemark
     [:name (or name "MapMarks Mark")]
     [:description (str "Address: " address "\n"
                        "Tags: " (str/join ", " tags) "\n"
                        "Notes: " (or notes ""))]
     [:Point
      [:coordinates (format "%f,%f,0" lon lat)]]]))

(defn- marks->kml [marks]
  (str (h/html
        {:mode :xml}
        (h/raw "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        [:kml {:xmlns "http://www.opengis.net/kml/2.2"}
         [:Document
          [:name "MapMarks Marks"]
          (map mark->placemark marks)]])))

(defn- format-rfc822 [iso-str]
  (try
    (let [inst (java.time.Instant/parse iso-str)
          zdt (java.time.ZonedDateTime/ofInstant inst (java.time.ZoneId/of "UTC"))
          formatter java.time.format.DateTimeFormatter/RFC_1123_DATE_TIME]
      (.format formatter zdt))
    (catch Exception _
      nil)))

(defn- mark->rss-item [base-url mark]
  (let [{:keys [name address town state tags expiration notes updated xt/id lat lon shared? creator]} mark
        title (or
               (when (not (str/blank? name)) name)
               (when (seq tags) (str/join ", " tags))
               "MapMarks Mark")
        full-address (str/join ", " (remove str/blank? [address town state]))
        description (str/join "\n"
                              (remove nil?
                                      [(when (seq full-address) (str "Address: " full-address))
                                       (when (seq tags) (str "Tags: " (str/join ", " tags)))
                                       (when (seq expiration) (str "Expires: " expiration))
                                       (when (seq notes) (str "Notes: " notes))
                                       (str "Coordinates: " lat ", " lon)
                                       (when (some? shared?) (str "Shared: " (if shared? "Yes" "No")))
                                       (when (seq creator) (str "Creator: " creator))]))]
    [:item
     [:title title]
     [:link (str base-url "#mark=" id)]
     [:description description]
     (when-let [pub-date (format-rfc822 updated)]
       [:pubDate pub-date])
     [:guid {:isPermaLink "false"} id]]))

(defn- marks->rss [marks base-url]
  (str (h/html
        {:mode :xml}
        (h/raw "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        [:rss {:version "2.0"
               :xmlns:atom "http://www.w3.org/2005/Atom"}
         [:channel
          [:title "MapMarks Marks"]
          [:link base-url]
          [:description "Latest MapMarks Marks"]
          [:atom:link {:href (str base-url "api/marks.rss") :rel "self" :type "application/rss+xml"}]
          (map (partial mark->rss-item base-url) marks)]])))

(defn get-marks-rss-handler [req]
  (let [identity (:identity req)
        marks (db/list-marks identity)
        base-url config/external-base-url
        rss (marks->rss marks base-url)]
    {:status 200
     :headers {"Content-Type" "application/rss+xml"
               "Content-Disposition" "inline"}
     :body rss}))

(defn get-marks-kml-handler [req]
  (let [identity (:identity req)
        marks (db/list-marks identity)
        kml (marks->kml marks)]
    {:status 200
     :headers {"Content-Type" "application/vnd.google-earth.kml+xml"
               "Content-Disposition" "attachment; filename=\"marks.kml\""}
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

(def MarkSchema logic/MarkSchema)

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

 (defn get-marks-handler
  [req]
  (tel/log! :info {:get-marks req})
  (let [identity (:identity req)
        params (:params req)
        lat (some-> (get params :lat) Double/parseDouble)
        lon (some-> (get params :lon) Double/parseDouble)
        since (get params :since)
        marks (db/list-marks identity {:lat lat
                                         :lon lon
                                         :radius logic/search-radius-km})
        results (mapv common-mark/select-mark-fields marks)
        now (common-utils/get-current-timestamp)
        deleted-ids (if since
                      (db/list-deletions identity since {:lat lat :lon lon :radius logic/search-radius-km})
                      [])]
    (api-response 200 {:marks results :deleted-ids deleted-ids :new-sync now})))

(defn get-mark-handler [req]
  (tel/log! :info {:get-mark req})
  (let [identity (:identity req)
        id (get-in req [:path-params :id])
        mark (db/get-mark id identity)]
    (if mark
      (api-response 200 (common-mark/select-mark-fields mark))
      (not-found))))

(defn create-mark-handler [req]
  (let [mark (-> (json/read-str (rur/body-string req) :key-fn keyword)
                  common-mark/select-mark-fields
                  (dissoc :creator))
        id (or (:id mark) (:xt/id mark) (common-utils/random-uuid-str))
        existing-mark (when id (db/get-mark-unfiltered id))
        mark-to-validate (dissoc mark :id :xt/id)]
    (tel/log! :info {:create-mark mark})
    (if (and existing-mark (not= (:creator existing-mark) (:identity req)))
      (api-response 403 {:error "Forbidden: You do not own this mark"})
      (if-not (m/validate MarkSchema mark-to-validate)
        (api-response 400 {:status "failed"
                           :errors (me/humanize (m/explain MarkSchema mark-to-validate))})
        (let [mark (assoc
                      mark
                      :xt/id id
                      :creator (or (:creator existing-mark) (:identity req)))
              mark (dissoc mark :id)]
          (db/save-mark mark)
          (api-response 201 (assoc mark :id id)))))))

(defn update-mark-handler [req]
  (let [id (or (get-in req [:path-params :id])
               (get-in req [:params :id]))
        mark (-> (json/read-str (rur/body-string req) :key-fn keyword)
                  common-mark/select-mark-fields
                  (dissoc :creator))
        existing-mark (when id (db/get-mark-unfiltered id))]
    (tel/log! :info {:update-mark mark})
    (if (and existing-mark (not= (:creator existing-mark) (:identity req)))
      (api-response 403 {:error "Forbidden: You do not own this mark"})
      (let [mark-to-validate (dissoc mark :id :xt/id)]
        (if-not (m/validate MarkSchema mark-to-validate)
          (api-response 400 {:status "failed"
                             :errors (me/humanize (m/explain MarkSchema mark-to-validate))})
          (let [final-id (or id (:id mark) (:xt/id mark)
                           (common-utils/random-uuid-str))
                mark (assoc
                        mark
                        :xt/id final-id
                        :creator (or (:creator existing-mark) (:identity req)))
                mark (dissoc mark :id)]
            (db/save-mark mark)
            (api-response 200 (assoc mark :id final-id))))))))

(defn delete-mark-handler [req]
  (tel/log! :info {:delete-mark req})
  (let [id (get-in req [:path-params :id])
        existing-mark (db/get-mark-unfiltered id)]
    (if (and existing-mark (not= (:creator existing-mark) (:identity req)))
      (api-response 403 {:error "Forbidden: You do not own this mark"})
      (do
        (db/delete-mark id)
        (api-response 200 {:message (format "'%s' deleted" id)})))))

(defn vote-mark-handler [req]
  (tel/log! :info {:vote-mark req})
  (let [id (get-in req [:path-params :id])
        identity (:identity req)
        body (json/read-str (rur/body-string req) :key-fn keyword)
        value (:value body)]
    (if (not identity)
      (api-response 401 {:error "Unauthorized"})
      (if (not (contains? #{1 -1 0} value))
        (api-response 400 {:error "Invalid vote value"})
        (do
          (db/vote-mark id identity value)
          (api-response 200 {:status "success"}))))))
