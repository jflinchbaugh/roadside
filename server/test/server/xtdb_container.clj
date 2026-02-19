(ns server.xtdb-container
  (:require [clj-test-containers.core :as tc]
            [xtdb.api :as xt]))

(def xtdb-container
  (tc/create {:image-name "ghcr.io/xtdb/xtdb:2.1.0"
              :exposed-ports [5432]
              :wait-for {:strategy :log
                         :message "XTDB started"}}))

(defn with-xtdb-client [f]
  (let [started-container (tc/start! xtdb-container)
        port (get (:mapped-ports started-container) 5432)
        host (:host started-container)
        node (xt/client {:host host :port port})]
    (try
      (f node)
      (finally
        (tc/stop! started-container)))))
