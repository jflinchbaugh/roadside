(ns server.xtdb-test
  (:require [clojure.test :refer [deftest is testing]]
            [xtdb.api :as xt]
            [xtdb.node :as xtn]))

(deftest in-memory-node-test
  (testing "Starting an in-memory XTDB node"
    (with-open [node (xtn/start-node {})]
      (is (not (nil? node)))
      (xt/submit-tx node [[:put-docs :users {:xt/id "test-user" :name "Test User"}]])
      (.await_token node)
      (let [result (xt/q node '(->
                                 (from :users [*])
                                 (where (= xt/id "test-user"))))]
        (is (= "Test User" (:name (first result))))))))
