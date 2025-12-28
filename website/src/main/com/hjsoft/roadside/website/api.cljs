(ns com.hjsoft.roadside.website.api
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [go <!]]))

(defn- auth-header [user password]
  {:basic-auth [user password]})

(defn fetch-stands [url user password]
  (go
    (let [response (<! (http/get url
                                 (merge (auth-header user password)
                                        {:accept :json})))]
      (if (:success response)
        {:success true :data (:body response)}
        {:success false :error (str "HTTP Error: " (:status response))}))))

(defn save-stands [url user password stands]
  (go
    (let [response (<! (http/put url
                                 (merge (auth-header user password)
                                        {:json-params stands
                                         :content-type :json})))]
      (if (:success response)
        {:success true}
        {:success false :error (str "HTTP Error: " (:status response))}))))
