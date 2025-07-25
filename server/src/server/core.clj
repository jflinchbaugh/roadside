(ns server.core
  (:require [org.httpkit.server :as http-kit]
            [reitit.ring :as ring]
            [taoensso.telemere :as telemere]))

(def app
  (ring/ring-handler
   (ring/router
    ["/" {:get {:handler (fn [_]
                           {:status 200
                            :body "Hello, World!"})}}])
   (ring/create-default-handler)))

(defn -main [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))]
    (http-kit/run-server app {:port port})
    (telemere/info :server/starting {:port port})))
