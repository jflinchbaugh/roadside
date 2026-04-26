(ns server.xtdb-container
  (:require [clj-test-containers.core :as tc]
            [xtdb.api :as xt]
            [taoensso.telemere :as tel]
            [clojure.string :as str]))

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
    ;; Clear all tables for isolation
    (let [tables (->> (xt/q node '(from :xt/tables [table-name]))
                      (map :table-name)
                      (remove #(str/starts-with? (name %) "xt"))
                      (into #{:users :stands :votes}))]
      (doseq [table tables]
        (let [docs (xt/q node (list 'from table '[xt/id]))]
          (when (seq docs)
            (tel/log! :info {:erase-docs docs})
            (xt/execute-tx
              node
              (mapv
                (fn [d] [:erase-docs table (:xt/id d)])
                docs))
            ;; Small delay to be extra sure indexing is stable
            (Thread/sleep 100)))))
    (f node)))
