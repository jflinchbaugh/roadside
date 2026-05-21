(ns com.hjsoft.mapmarks.website.api
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [go <!]]
            [clojure.string :as str]
            [taoensso.telemere :as tel]))

(defn- site-url [site path]
  (str "s/" site "/api/" path))

(defn- with-auth-opts
  ([user password] (with-auth-opts user password {}))
  ([user password opts]
   (if (and user password)
     (assoc opts :basic-auth {:username user :password password})
     opts)))

(def default-http-deps
  {:get http/get
   :post http/post
   :put http/put
   :delete http/delete})

(defn- extract-error [response default-msg]
  (let [body (:body response)]
    (cond
      (and (map? body) (:errors body))
      (let [errors (:errors body)]
        (cond
          (map? errors) (map (fn [[k v]] (str (name k) ": " (str/join "; " v))) errors)
          (coll? errors) errors
          :else [errors]))

      (and (map? body) (:message body))
      [(:message body)]

      :else
      [(or (:status-text response) default-msg)])))

(defn fetch-marks
  ([site user password]
   (fetch-marks site user password nil nil nil default-http-deps))
  ([site user password lat lng since]
   (fetch-marks site user password lat lng since default-http-deps))
  ([site user password lat lng since {:keys [get]}]
   (go
     (let [url (site-url site "marks")
           params (cond-> {}
                    lat (assoc :lat lat)
                    lng (assoc :lon lng)
                    since (assoc :since since))
           response (<! (get url
                             (with-auth-opts user password
                               {:query-params params})))]
       (if (:success response)
         {:success true
          :data (:body response)}
         {:success false
          :error (extract-error response (str "HTTP Error: " url ", " (:status response)))})))))

(defn create-mark
  ([site user password mark] (create-mark site user password mark default-http-deps))
  ([site user password mark {:keys [post]}]
   (go
     (let [url (site-url site "marks")
           response (<! (post url
                              (with-auth-opts user password
                                {:json-params (assoc mark :site site)})))]
       (if (:success response)
         {:success true :data (:body response)}
         {:success false :error (extract-error response (str "HTTP Error: " (:status response)))})))))

(defn update-mark
  ([site user password mark] (update-mark site user password mark default-http-deps))
  ([site user password mark {:keys [put]}]
   (let [id (:id mark)
         url (site-url site (str "marks/" id))]
     (go
       (let [response (<! (put url
                               (with-auth-opts user password
                                 {:json-params (assoc mark :site site)})))]
         (if (:success response)
           {:success true :data (:body response)}
           {:success false :error (extract-error response (str "HTTP Error: " (:status response)))}))))))

(defn delete-mark
  ([site user password mark-id]
   (delete-mark site user password mark-id default-http-deps))
  ([site user password mark-id {:keys [delete]}]
   (let [url (site-url site (str "marks/" mark-id))]
     (go
       (let [response (<! (delete url
                                  (with-auth-opts user password)))]
         (if (:success response)
           {:success true}
           {:success false
            :error (extract-error response (str "HTTP Error: " (:status response)))}))))))

(defn vote-mark
  ([site user password mark-id value]
   (vote-mark site user password mark-id value default-http-deps))
  ([site user password mark-id value {:keys [post]}]
   (let [url (site-url site (str "marks/" mark-id "/vote"))]
     (go
       (let [response (<! (post url
                                (with-auth-opts user password
                                  {:json-params {:value value}})))]
         (if (:success response)
           {:success true}
           {:success false
            :error (extract-error response (str "HTTP Error: " (:status response)))}))))))

(defn geocode-address
  ([site user password address]
   (geocode-address site user password address default-http-deps))
  ([site user password address {:keys [get]}]
   (go
     (let [url (site-url site "geocode")
           params {:q address}
           response (<! (get url (with-auth-opts user password
                                   {:query-params params})))]
       (if (and (:success response) (seq (:body response)))
         (let [result (first (:body response))]
           {:success true
            :lat (js/parseFloat (:lat result))
            :lng (js/parseFloat (:lon result))})
         {:success false
          :error (or (:status-text response) "Address not found")})))))

(defn reverse-geocode
  ([site user password lat lng]
   (reverse-geocode site user password lat lng default-http-deps))
  ([site user password lat lng {:keys [get]}]
   (go
     (let [url (site-url site "reverse-geocode")
           params {:lat lat :lon lng}
           response (<! (get url (with-auth-opts user password
                                   {:query-params params})))]
       (if (:success response)
         {:success true :data (:body response)}
         {:success false
          :error (or (:status-text response) "Location not found")})))))

(defn register-user
  ([site user password email]
   (register-user site user password email default-http-deps))
  ([site user password email {:keys [post]}]
   (go
     (let [url (site-url site "register")
           params {:login user
                   :password password
                   :email email
                   :site site}
           response (<! (post url {:form-params params}))]
       (tel/log! :info {:register-user {:params params :response response}})
       (if (= 201 (:status response))
         {:success true :data (:body response)}
         {:success false
          :error (extract-error response (str "HTTP Error: " (:status response)))})))))
