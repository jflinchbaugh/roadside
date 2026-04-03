(ns server.core
  (:gen-class)
  (:require [org.httpkit.server :as hks]
            [reitit.ring :as ring]
            [ring.middleware.defaults :as rmd]
            [ring.middleware.cors :as rmc]
            [server.db :as db]
            [server.auth :as auth]
            [server.handlers :as handlers]
            [xtdb.api :as xt]
            [taoensso.telemere :as tel]))

(tel/set-min-level! :debug)

(def ^:const base-url "/roadside")

(def app
  (-> [base-url
       ["/api"
        ["/ping" handlers/ping-handler]
        ["/geocode" {:middleware [auth/wrap-auth auth/identity-required-wrapper]
                     :get handlers/geocode-handler}]
        ["/reverse-geocode" {:middleware [auth/wrap-auth auth/identity-required-wrapper]
                             :get handlers/reverse-geocode-handler}]
        ["/register" {:post handlers/register-handler}]
        ["/stands.csv" {:middleware [auth/wrap-auth]
                        :get handlers/get-stands-csv-handler}]
        ["/stands.kml" {:middleware [auth/wrap-auth]
                        :get handlers/get-stands-kml-handler}]
        ["/stands" {:middleware [auth/wrap-auth]
                    :get handlers/get-stands-handler
                    :post {:middleware [auth/identity-required-wrapper]
                           :handler handlers/create-stand-handler}}]
        ["/stands/:id" {:middleware [auth/wrap-auth]
                        :get handlers/get-stand-handler
                        :put {:middleware [auth/identity-required-wrapper]
                              :handler handlers/update-stand-handler}
                        :delete {:middleware [auth/identity-required-wrapper]
                                 :handler handlers/delete-stand-handler}}]]]
      (ring/router)
      (ring/ring-handler
       (ring/routes
        (ring/create-resource-handler {:path base-url})
        handlers/not-found))
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
  (reset! db/node nil))

(defn start-server!
  [port db-host]
  (if (nil? @server)
    (let [new-node (xt/client {:host db-host})]
      (reset! db/node new-node)
      (db/migrate-stands!)
      (reset! server (hks/run-server #'app {:port port}))
      (tel/log! :info {:server-started {:port port :db-host db-host}}))
    "server already running"))

(defn -main [& [port db-host]]
  (if (or (nil? port) (nil? db-host))
    (println "Usage: <port> <xtdb-url>")
    (start-server! (Integer/parseInt port) db-host)))
