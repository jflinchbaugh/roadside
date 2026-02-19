(ns com.hjsoft.roadside.website.api
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [go <!]]))

(defn fetch-stands [url user password]
  (go
    (let [response (<! (http/get url
                                 {:basic-auth {:username user :password password}}))]
      (if (:success response)
        {:success true :data (:body response)}
        {:success false :error (str "HTTP Error: " url ", " (:status response))}))))

(defn create-stand [url user password stand]
  (go
    (let [response (<! (http/post url
                                  {:basic-auth {:username user :password password}
                                   :json-params stand}))]
      (if (:success response)
        {:success true :data (:body response)}
        {:success false :error (str "HTTP Error: " (:status response))}))))

(defn update-stand [url user password stand]
  (let [id (:id stand)
        resource-url (str url (when-not (.endsWith url "/") "/") id)]
    (go
      (let [response (<! (http/put resource-url
                                   {:basic-auth {:username user :password password}
                                    :json-params stand}))]
        (if (:success response)
          {:success true :data (:body response)}
          {:success false :error (str "HTTP Error: " (:status response))})))))

(defn delete-stand [url user password stand-id]
  (let [resource-url (str url (when-not (.endsWith url "/") "/") stand-id)]
    (go
      (let [response (<! (http/delete resource-url
                                      {:basic-auth {:username user :password password}}))]
        (if (:success response)
          {:success true}
          {:success false :error (str "HTTP Error: " (:status response))})))))
