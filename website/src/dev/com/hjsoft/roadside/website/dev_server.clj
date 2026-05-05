(ns com.hjsoft.roadside.website.dev-server
  (:require [org.httpkit.client :as http]
            [clojure.string :as str]))

(def target "http://localhost:7000/roadside")

(defn handler [req]
  (let [uri (:uri req)]
    (if (str/starts-with? uri "/api")
      (let [url (str target uri (when-let [qs (:query-string req)] (str "?" qs)))]
        (let [{:keys [status headers body error]}
              @(http/request
                {:method (:request-method req)
                 :url url
                 :headers (dissoc (:headers req) "host" "content-length")
                 :body (:body req)
                 :as :stream})]
          (if (or error (nil? status))
            {:status 502
             :headers {"Content-Type" "text/plain"}
             :body (str "Bad Gateway: " (or (str error) "Remote target unavailable"))}
            {:status status
             :headers (update-keys (or headers {}) (fn [k] (if (keyword? k) (name k) (str k))))
             :body body})))
      nil)))
