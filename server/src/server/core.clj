(ns server.core
  (:gen-class)
  (:require [org.httpkit.server :as hks]
            [reitit.ring :as ring]
            [ring.middleware.defaults :as rmd]
            [ring.middleware.cors :as rmc]
            [ring.util.request :as rur]
            [buddy.auth.middleware :as buddy]
            [buddy.auth.backends :as backends]
            [clojure.data.json :as json]
            [tick.core :as t]
            [xtdb.api :as xt]
            [taoensso.telemere :as tel]))

(defonce node (atom nil))

(def ^:const realm "roadside")

(def ^:const base-url "/roadside")

(defn api-response
  [code document]
  {:status code
   :headers {"Content-Type" "application/json"}
   :body (json/write-str document)})

(defn not-found
  [& _]
  (api-response
   404
   {:error "Not Found"}))

(defn unauthorized
  [& _]
  {:status 401
   :headers {"Content-Type" "application/json"
             "WWW-Authenticate" (format "Basic realm=%s" realm)}
   :body (json/write-str {:error "Unauthorized"})})

(defn ping-handler
  [_]
  (api-response 200 "pong"))

(defn register-handler
  [req]
  (let [id (or (get-in req [:params :id]) (str (java.util.UUID/randomUUID)))
        login (get-in req [:params :login])
        password (get-in req [:params :password])
        user {:xt/id id
              :login login
              :password password
              :updated (str (t/now))}]
    (xt/submit-tx @node [[:put-docs :users user]])
    (api-response 201 {:login login})))

(defn get-stands-handler
  [_req]
  (let [stands (vec (xt/q @node '(from :stands [*])))]
    (api-response 200 stands)))

(defn get-stand-handler
  [req]
  (let [id (get-in req [:path-params :id])
        stand (first
               (xt/q @node
                     ['(fn [id]
                         (->
                          (from :stands [*])
                          (where (= xt/id id))))
                      id]))]
    (if stand
      (api-response 200 stand)
      (not-found))))

(defn create-stand-handler
  [req]
  (let [stand (json/read-str (rur/body-string req) :key-fn keyword)
        stand (assoc stand :xt/id (or (:id stand) (:xt/id stand) (str (java.util.UUID/randomUUID)))
                     :updated (str (t/now))
                     :creator (:identity req))
        stand (dissoc stand :id)]
    (xt/submit-tx @node [[:put-docs :stands stand]])
    (api-response 201 stand)))

(defn update-stand-handler
  [req]
  (let [id (get-in req [:path-params :id])
        stand (json/read-str (rur/body-string req) :key-fn keyword)
        stand (assoc stand :xt/id id :updated (str (t/now)))
        stand (dissoc stand :id)]
    (xt/submit-tx @node [[:put-docs :stands stand]])
    (api-response 200 stand)))

(defn delete-stand-handler
  [req]
  (let [id (get-in req [:path-params :id])]
    (xt/submit-tx @node [[:delete-docs :stands id]])
    (api-response 200 {:message (format "'%s' deleted" id)})))

(defn identity-required-wrapper
  [handler]
  (fn [req]
    (if (nil? (:identity req))
      (unauthorized)
      (handler req))))

(defn my-authfn
  [req authdata]
  (let [login (:username authdata)
        password (:password authdata)
        user (first
              (xt/q @node
                    ['(fn [l p]
                        (-> (from :users [login password])
                            (where (= login l))
                            (where (= password p))))
                     login password]))]
    (when user
      (:login user))))

(def backend (backends/basic {:realm realm :authfn my-authfn}))

(defn authenticated-for-logger [handler]
  (buddy/wrap-authentication handler backend))

(def app
  (-> [base-url
       ["/api"
        ["/ping" ping-handler]
        ["/register" {:post register-handler}]
        ["/stands" {:middleware
                    [authenticated-for-logger identity-required-wrapper]
                    :get get-stands-handler
                    :post create-stand-handler}]
        ["/stands/:id" {:middleware
                        [authenticated-for-logger identity-required-wrapper]
                        :get get-stand-handler
                        :put update-stand-handler
                        :delete delete-stand-handler}]]]
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
    (reset! server nil))
  (reset! node nil))

(defn start-server!
  [port db-host]
  (if (nil? @server)
    (do
      (reset! node (xt/client {:host db-host}))
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
