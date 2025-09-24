(ns server.core
  (:gen-class)
  (:require [org.httpkit.server :as hks]
            [reitit.ring :as ring]
            [ring.middleware.defaults :as rmd]
            [ring.middleware.cors :as rmc]
            [ring.util.request :as rur]
            [buddy.auth.middleware :as buddy]
            [buddy.auth.backends :as backends]
            [xtdb.api :as xt]
            [taoensso.telemere :as tel]))

(def ^:const realm "roadside")

(def ^:const base-url "/roadside")

(defonce storage (atom {}))

(defn api-response
  [code document]
  {:status code
   :headers {"Content-Type" "text/plain"}
   :body document})

(defn not-found
  [& _]
  (api-response
   404
   "Not Found"))

(defn unauthorized
  [& _]
  {:status 401
   :headers {"Content-Type" "text/plain"
             "WWW-Authenticate" (format "Basic realm=%s" realm)}
   :body "Unauthorized"})

(defn ping-handler
  [_]
  (api-response 200 "pong"))

(defn download-handler
  [req]
  (if-not (:identity req)
    (not-found)
    (let [logger (get-logger req)]
      (api-response
       200
       nil))))

(defn upload-handler
  [req]
  (if-not (:identity req)
    (not-found)
    (let [request-body (rur/body-string req)
          id (get-logger req)]
      (api-response
       200
       request-body))))

(defn owner?
  [login logger]
  (= (:login logger) login))

(defn register-handler
  [req]
  (let [id (get-in req [:params :id])
        login (get-in req [:params :login])
        password (get-in req [:params :password])
        resource (format "%s/api/document/%s" base-url id)]
    (if (get @storage id)
      (api-response 200 (format "'%s' already exists" id))
      (do
        (api-response
          200
          (format "'%s' created. Access it as '%s'." id resource))))))

(defn delete-handler
  [req]
  (let [id nil
        login (:identity req)]
    (if-not (and logger (owner? login logger))
      (not-found)
      (do
        (api-response 200 (format "'%s' deleted" id))))))

(defn identity-required-wrapper
  [handler]
  (fn [req]
    (if (nil? (:identity req))
      (unauthorized)
      (handler req))))

(defn my-authfn
  [req authdata]
  (let [login (:username authdata)
        password (:password authdata)]
    (when (= [login password] [login password])
      login)))

(def backend (backends/basic {:realm realm :authfn my-authfn}))

(defn authenticated-for-logger [handler]
  (buddy/wrap-authentication handler backend))

(def app
  (-> [base-url
       ["/api"
        ["/ping" ping-handler]
        ["/register" {:post register-handler}]
        ["/stands/:id" {:middleware
                        [authenticated-for-logger identity-required-wrapper]
                        :get download-handler
                        :post upload-handler
                        :delete unregister-handler}]]]
      (ring/router)
      (ring/ring-handler
       (ring/routes
        (ring/create-resource-handler {:path base-url})
        not-found))
      (rmc/wrap-cors
       :access-control-allow-origin [#".*"]
       :access-control-allow-methods [:get :put :post :delete])
      (rmd/wrap-defaults
       (assoc
        rmd/api-defaults
        :proxy true))))

(defonce server (atom nil))

(defn stop-server!
  []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn start-server!
  [port db-host]
  (if (nil? @server)
    (do
      (connect-db db-host)
      (reset! server (hks/run-server #'app {:port port}))
      (tel/log! :info "Server started."))
    "server already running"))

(defn -main [& [port db-host]]
  (if (or (nil? port) (nil? db-host))
    (println "Usage: <port> <xtdb-url>")
    (start-server! (Integer/parseInt port) db-host)))

(comment

  (start-server! 8080 "localhost")

  (stop-server!)

  (app {:scheme :http :request-method :get :uri "/roadside/api/ping"})

  (let [node (xt/client {:host "localhost"})]
    (xt/q node '(from :roadside [*])))

  nil)
