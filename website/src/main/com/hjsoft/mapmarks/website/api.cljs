(ns com.hjsoft.mapmarks.website.api
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [go <!]]
            [clojure.string :as str]
            [taoensso.telemere :as tel]))

(def ^:private marks-url "api/marks")

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
  ([user password]
   (fetch-marks user password nil nil nil default-http-deps))
  ([user password lat lng since]
   (fetch-marks user password lat lng since default-http-deps))
  ([user password lat lng since {:keys [get]}]
   (go
     (let [params (cond-> {}
                    lat (assoc :lat lat)
                    lng (assoc :lon lng)
                    since (assoc :since since))
           response (<! (get marks-url
                             (with-auth-opts user password
                               {:query-params params})))]
       (if (:success response)
         {:success true
          :data (:body response)}
         {:success false
          :error (extract-error response (str "HTTP Error: " marks-url ", " (:status response)))})))))

(defn create-mark
  ([user password mark] (create-mark user password mark default-http-deps))
  ([user password mark {:keys [post]}]
   (go
     (let [response (<! (post marks-url
                              (with-auth-opts user password
                                {:json-params mark})))]
       (if (:success response)
         {:success true :data (:body response)}
         {:success false :error (extract-error response (str "HTTP Error: " (:status response)))})))))

(defn update-mark
  ([user password mark] (update-mark user password mark default-http-deps))
  ([user password mark {:keys [put]}]
   (let [id (:id mark)
         resource-url (str marks-url "/" id)]
     (go
       (let [response (<! (put resource-url
                               (with-auth-opts user password
                                 {:json-params mark})))]
         (if (:success response)
           {:success true :data (:body response)}
           {:success false :error (extract-error response (str "HTTP Error: " (:status response)))}))))))

(defn delete-mark
  ([user password mark-id]
   (delete-mark user password mark-id default-http-deps))
  ([user password mark-id {:keys [delete]}]
   (let [resource-url (str marks-url "/" mark-id)]
     (go
       (let [response (<! (delete resource-url
                                  (with-auth-opts user password)))]
         (if (:success response)
           {:success true}
           {:success false
            :error (extract-error response (str "HTTP Error: " (:status response)))}))))))

(defn vote-mark
  ([user password mark-id value]
   (vote-mark user password mark-id value default-http-deps))
  ([user password mark-id value {:keys [post]}]
   (let [resource-url (str marks-url "/" mark-id "/vote")]
     (go
       (let [response (<! (post resource-url
                                (with-auth-opts user password
                                  {:json-params {:value value}})))]
         (if (:success response)
           {:success true}
           {:success false
            :error (extract-error response (str "HTTP Error: " (:status response)))}))))))

(defn geocode-address
  ([user password address]
   (geocode-address user password address default-http-deps))
  ([user password address {:keys [get]}]
   (go
     (let [url "api/geocode"
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
  ([user password lat lng]
   (reverse-geocode user password lat lng default-http-deps))
  ([user password lat lng {:keys [get]}]
   (go
     (let [url "api/reverse-geocode"
           params {:lat lat :lon lng}
           response (<! (get url (with-auth-opts user password
                                   {:query-params params})))]
       (if (:success response)
         {:success true :data (:body response)}
         {:success false
          :error (or (:status-text response) "Location not found")})))))

(defn register-user
  ([user password email]
   (register-user user password email default-http-deps))
  ([user password email {:keys [post]}]
   (go
     (let [url "api/register"
           params {:login user
                   :password password
                   :email email}
           response (<! (post url {:form-params params}))]
       (tel/log! :info {:register-user {:params params :response response}})
       (if (= 201 (:status response))
         {:success true :data (:body response)}
         {:success false
          :error (extract-error response (str "HTTP Error: " (:status response)))})))))
