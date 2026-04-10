(ns server.auth
  (:require [buddy.auth.backends :as backends]
            [buddy.hashers :as hashers]
            [server.db :as db]
            [clojure.data.json :as json]
            [buddy.auth.middleware :as buddy]))

(def ^:const realm "roadside")

(defn unauthorized
  [& _]
  {:status 401
   :headers {"Content-Type" "application/json"
             "WWW-Authenticate" (format "Basic realm=%s" realm)}
   :body (json/write-str {:error "Unauthorized"})})

(defn- authfn
  [_req {:keys [username password]}]
  (let [user (db/get-user username)]
    (when (and user
               (not= false (:enabled? user))
               (:valid (hashers/verify password (:password user))))
      (:login user))))

(def backend (backends/basic {:realm realm :authfn authfn}))

(defn wrap-auth [handler]
  (buddy/wrap-authentication handler backend))

(defn identity-required-wrapper
  [handler]
  (fn [req]
    (if (nil? (:identity req))
      (unauthorized)
      (handler req))))
