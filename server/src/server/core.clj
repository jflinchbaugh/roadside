(ns server.core
  (:require [org.httpkit.server :as http-kit]
            [reitit.ring :as ring]))

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
    (println "Server running on port" port)))
