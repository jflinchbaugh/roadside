(ns com.hjsoft.roadside.website.api
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [go <!]]))

(def ^:private stands-url "api/stands")

(defn fetch-stands [user password]
  (go
    (let [response (<! (http/get stands-url
                                 {:basic-auth {:username user :password password}}))]
      (if (:success response)
        {:success true :data (:body response)}
        {:success false :error (str "HTTP Error: " stands-url ", " (:status response))}))))

(defn create-stand [user password stand]
  (go
    (let [response (<! (http/post stands-url
                                  {:basic-auth {:username user :password password}
                                   :json-params stand}))]
      (if (:success response)
        {:success true :data (:body response)}
        {:success false :error (str "HTTP Error: " (:status response))}))))

(defn update-stand [user password stand]
  (let [id (:id stand)
        resource-url (str stands-url "/" id)]
    (go
      (let [response (<! (http/put resource-url
                                   {:basic-auth {:username user :password password}
                                    :json-params stand}))]
        (if (:success response)
          {:success true :data (:body response)}
          {:success false :error (str "HTTP Error: " (:status response))})))))

(defn delete-stand [user password stand-id]
  (let [resource-url (str stands-url "/" stand-id)]
    (go
      (let [response (<! (http/delete resource-url
                                      {:basic-auth {:username user :password password}}))]
        (if (:success response)
          {:success true}
          {:success false :error (str "HTTP Error: " (:status response))})))))
