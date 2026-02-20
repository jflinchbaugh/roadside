(ns server.xtdb-container
  (:require [clj-test-containers.core :as tc]
            [xtdb.api :as xt]))

(defonce xtdb-container
  (delay
    (tc/start!
      (tc/create {:image-name "ghcr.io/xtdb/xtdb:2.1.0"
                  :exposed-ports [5432]
                  :wait-for {:strategy :log
                             :message "XTDB started"}}))))

(defn with-xtdb-client [f]
  (let [started-container @xtdb-container
        port (get (:mapped-ports started-container) 5432)
        host (:host started-container)
        node (xt/client {:host host :port port})]
    ;; Clear tables for isolation
    (doseq [table [:users :stands]]
      (let [docs (xt/q node (list 'from table '[xt/id]))]
        (when (seq docs)
          (xt/execute-tx node (mapv (fn [d] [:delete-docs table (:xt/id d)]) docs)))))
    (f node)))
