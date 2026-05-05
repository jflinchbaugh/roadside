(ns com.hjsoft.roadside.website.dev-server
  (:require [org.httpkit.client :as http]
            [clojure.string :as str]))

(def target "http://localhost:7000/roadside")

(defn handler [req]
  (let [uri (:uri req)]
    (if (str/starts-with? uri "/api")
      (let [url (str target uri (when-let [qs (:query-string req)] (str "?" qs)))]
        (let [resp @(http/request
                     {:method (:request-method req)
                      :url url
                      :headers (dissoc (:headers req) "host" "content-length")
                      :body (:body req)
                      :as :stream})]
          {:status (:status resp)
           :headers (update-keys (:headers resp) (fn [k] (if (keyword? k) (name k) (str k))))
           :body (:body resp)}))
      nil)))
