(ns server.xtdb-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [xtdb.api :as xt]
            [clj-test-containers.core :as tc]))

(def xtdb-container
  (tc/create {:image-name "ghcr.io/xtdb/xtdb:2.1.0"
              :exposed-ports [5432]
              :wait-for {:strategy :log
                         :message "XTDB started"}}))

(def ^:dynamic *node* nil)

(defn with-xtdb-container [f]
  (let [started-container (tc/start! xtdb-container)
        port (get (:mapped-ports started-container) 5432)
        host (:host started-container)
        node (xt/client {:host host :port port})]
    (binding [*node* node]
      (f))
    (tc/stop! started-container)))

(use-fixtures :once with-xtdb-container)

(deftest container-node-test
  (testing "Using an XTDB node in a container"
    (let [node *node*]
      (is (not (nil? node)))
      (xt/submit-tx node [[:put-docs :users {:xt/id "test-user" :name "Test User"}]])
      (let [result (xt/q node '(->
                                 (from :users [*])
                                 (where (= xt/id "test-user"))))]
        (is (= "Test User" (:name (first result))))))))
