(ns com.hjsoft.roadside.website.api
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [go <!]]
            [clojure.string :as str]))

(defn- stands-url [base-url]
  (str base-url (if (str/ends-with? base-url "/") "" "/") "stands"))

(defn fetch-stands [base-url user password]
  (let [url (stands-url base-url)]
    (go
      (let [response (<! (http/get url
                                   {:basic-auth {:username user :password password}}))]
        (if (:success response)
          {:success true :data (:body response)}
          {:success false :error (str "HTTP Error: " url ", " (:status response))})))))

(defn create-stand [base-url user password stand]
  (let [url (stands-url base-url)]
    (go
      (let [response (<! (http/post url
                                    {:basic-auth {:username user :password password}
                                     :json-params stand}))]
        (if (:success response)
          {:success true :data (:body response)}
          {:success false :error (str "HTTP Error: " (:status response))})))))

(defn update-stand [base-url user password stand]
  (let [id (:id stand)
        url (stands-url base-url)
        resource-url (str url "/" id)]
    (go
      (let [response (<! (http/put resource-url
                                   {:basic-auth {:username user :password password}
                                    :json-params stand}))]
        (if (:success response)
          {:success true :data (:body response)}
          {:success false :error (str "HTTP Error: " (:status response))})))))

(defn delete-stand [base-url user password stand-id]
  (let [url (stands-url base-url)
        resource-url (str url "/" stand-id)]
    (go
      (let [response (<! (http/delete resource-url
                                      {:basic-auth {:username user :password password}}))]
        (if (:success response)
          {:success true}
          {:success false :error (str "HTTP Error: " (:status response))})))))
