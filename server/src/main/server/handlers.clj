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

(defn- marks->csv [marks cfg]
  (let [header ["Name" "Latitude" "Longitude" "Address" "Town" "State"
                (:tags-name-plural cfg) "Notes"]
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

(defn- get-site [req]
  (or (get-in req [:path-params :site])
      config/site))

(defn get-marks-csv-handler [req]
  (let [identity (:identity req)
        site (get-site req)
        cfg (config/get-config site)
        marks (db/list-marks identity site)
        csv (marks->csv marks cfg)
        filename (str (str/lower-case (:mark-name-plural cfg)) ".csv")]
    {:status 200
     :headers {"Content-Type" "text/csv"
               "Content-Disposition"
               (str "attachment; filename=\"" filename "\"")}
     :body csv}))

(defn- mark->placemark [cfg mark]
  (let [{:keys [name lat lon address tags notes]} mark]
    [:Placemark
     [:name (or name (:mark-name-singular cfg))]
     [:description (str "Address: " address "\n"
                        (:tags-name-plural cfg) ": " (str/join ", " tags) "\n"
                        "Notes: " (or notes ""))]
     [:Point
      [:coordinates (format "%f,%f,0" lon lat)]]]))

(defn- marks->kml [marks cfg]
  (str (h/html
        {:mode :xml}
        (h/raw "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        [:kml {:xmlns "http://www.opengis.net/kml/2.2"}
         [:Document
          [:name (:app-name cfg)]
          (map (partial mark->placemark cfg) marks)]])))

(defn- format-rfc822 [iso-str]
  (try
    (let [inst (java.time.Instant/parse iso-str)
          zdt (java.time.ZonedDateTime/ofInstant inst (java.time.ZoneId/of "UTC"))
          formatter java.time.format.DateTimeFormatter/RFC_1123_DATE_TIME]
      (.format formatter zdt))
    (catch Exception _
      nil)))

(defn- mark->rss-item [base-url cfg mark]
  (let [{:keys [name address town state tags expiration notes updated
                xt/id lat lon shared? creator]} mark
        title (or
               (when (not (str/blank? name)) name)
               (when (seq tags) (str/join ", " tags))
               (:mark-name-singular cfg))
        full-address (str/join ", " (remove str/blank? [address town state]))
        description (str/join "\n"
                              (remove nil?
                                      [(when (seq full-address)
                                         (str "Address: " full-address))
                                       (when (seq tags)
                                         (str (:tags-name-plural cfg)
                                              ": " (str/join ", " tags)))
                                       (when (seq expiration)
                                         (str "Expires: " expiration))
                                       (when (seq notes)
                                         (str "Notes: " notes))
                                       (str "Coordinates: " lat ", " lon)
                                       (when (some? shared?)
                                         (str "Shared: "
                                              (if shared? "Yes" "No")))
                                       (when (seq creator)
                                         (str "Creator: " creator))]))
        anchor (str/lower-case (:mark-name-singular cfg))]
    [:item
     [:title title]
     [:link (str base-url "#" anchor "=" id)]
     [:description description]
     (when-let [pub-date (format-rfc822 updated)]
       [:pubDate pub-date])
     [:guid {:isPermaLink "false"} id]]))

(defn- marks->rss [marks base-url site cfg]
  (str (h/html
        {:mode :xml}
        (h/raw "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        [:rss {:version "2.0"
               :xmlns:atom "http://www.w3.org/2005/Atom"}
         [:channel
          [:title (:app-name cfg)]
          [:link base-url]
          [:description (:app-description cfg)]
          [:atom:link {:href (str base-url "s/" site "/feed.rss")
                       :rel "self"
                       :type "application/rss+xml"}]
          (map (partial mark->rss-item base-url cfg) marks)]])))

(defn get-marks-rss-handler [req]
  (let [identity (:identity req)
        site (get-site req)
        cfg (config/get-config site)
        marks (db/list-marks identity site)
        base-url config/external-base-url
        rss (marks->rss marks base-url site cfg)]
    {:status 200
     :headers {"Content-Type" "application/rss+xml"
               "Content-Disposition" "inline"}
     :body rss}))

(defn get-marks-kml-handler [req]
  (let [identity (:identity req)
        site (get-site req)
        cfg (config/get-config site)
        marks (db/list-marks identity site)
        kml (marks->kml marks cfg)
        filename (str (str/lower-case (:mark-name-plural cfg)) ".kml")]
    {:status 200
     :headers {"Content-Type" "application/vnd.google-earth.kml+xml"
               "Content-Disposition"
               (str "attachment; filename=\"" filename "\"")}
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
        site (or (get-in req [:params :site]) (get-site req))
        user-data {:login login :password password :email email :enabled? true :site site}]
    (if-not (m/validate UserSchema user-data)
      (api-response 400 {:status "failed"
                         :errors (me/humanize (m/explain UserSchema user-data))})
      (if (db/get-user login site)
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
        site (get-site req)
        lat (some-> (get params :lat) Double/parseDouble)
        lon (some-> (get params :lon) Double/parseDouble)
        since (get params :since)
        marks (db/list-marks identity site {:lat lat
                                             :lon lon
                                             :radius logic/search-radius-km})
        results (mapv common-mark/select-mark-fields marks)
        now (common-utils/get-current-timestamp)
        deleted-ids (if since
                      (db/list-deletions identity site since {:lat lat :lon lon :radius logic/search-radius-km})
                      [])]
    (api-response 200 {:marks results :deleted-ids deleted-ids :new-sync now})))

(defn get-mark-handler [req]
  (tel/log! :info {:get-mark req})
  (let [identity (:identity req)
        id (get-in req [:path-params :id])
        site (get-site req)
        mark (db/get-mark id identity site)]
    (if mark
      (api-response 200 (common-mark/select-mark-fields mark))
      (not-found))))

(defn create-mark-handler [req]
  (let [mark (-> (json/read-str (rur/body-string req) :key-fn keyword)
                  common-mark/select-mark-fields
                  (dissoc :creator))
        id (or (:id mark) (:xt/id mark) (common-utils/random-uuid-str))
        site (or (:site mark) (get-site req))
        existing-mark (when id (db/get-mark-unfiltered id site))
        mark-to-validate (assoc (dissoc mark :id :xt/id) :site site)]
    (tel/log! :info {:create-mark mark})
    (if (and existing-mark (not= (:creator existing-mark) (:identity req)))
      (api-response 403 {:error "Forbidden: You do not own this mark"})
      (if-not (m/validate MarkSchema mark-to-validate)
        (api-response 400 {:status "failed"
                           :errors (me/humanize (m/explain MarkSchema mark-to-validate))})
        (let [mark (assoc
                      mark
                      :xt/id id
                      :site site
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
        site (or (:site mark) (get-site req))
        existing-mark (when id (db/get-mark-unfiltered id site))]
    (tel/log! :info {:update-mark mark})
    (if (and existing-mark (not= (:creator existing-mark) (:identity req)))
      (api-response 403 {:error "Forbidden: You do not own this mark"})
      (let [mark-to-validate (assoc (dissoc mark :id :xt/id) :site site)]
        (if-not (m/validate MarkSchema mark-to-validate)
          (api-response 400 {:status "failed"
                             :errors (me/humanize (m/explain MarkSchema mark-to-validate))})
          (let [final-id (or id (:id mark) (:xt/id mark)
                           (common-utils/random-uuid-str))
                mark (assoc
                        mark
                        :xt/id final-id
                        :site site
                        :creator (or (:creator existing-mark) (:identity req)))
                mark (dissoc mark :id)]
            (db/save-mark mark)
            (api-response 200 (assoc mark :id final-id))))))))

(defn delete-mark-handler [req]
  (tel/log! :info {:delete-mark req})
  (let [id (get-in req [:path-params :id])
        site (get-site req)
        existing-mark (db/get-mark-unfiltered id site)]
    (if (and existing-mark (not= (:creator existing-mark) (:identity req)))
      (api-response 403 {:error "Forbidden: You do not own this mark"})
      (do
        (db/delete-mark id)
        (api-response 200 {:message (format "'%s' deleted" id)})))))

(defn vote-mark-handler [req]
  (tel/log! :info {:vote-mark req})
  (let [id (get-in req [:path-params :id])
        identity (:identity req)
        site (get-site req)
        body (json/read-str (rur/body-string req) :key-fn keyword)
        value (:value body)]
    (if (not identity)
      (api-response 401 {:error "Unauthorized"})
      (if (not (contains? #{1 -1 0} value))
        (api-response 400 {:error "Invalid vote value"})
        (do
          (db/vote-mark id identity value site)
          (api-response 200 {:status "success"}))))))
