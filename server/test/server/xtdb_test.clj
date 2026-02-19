(ns server.xtdb-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [xtdb.api :as xt]
            [server.xtdb-container :as xtn]))

(def ^:dynamic *node* nil)

(defn with-xtdb-container [f]
  (xtn/with-xtdb-client
    (fn [n]
      (binding [*node* n]
        (f)))))

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
