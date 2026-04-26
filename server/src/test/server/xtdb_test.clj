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

(use-fixtures :each with-xtdb-container)

(deftest container-node-test
  (testing "Using an XTDB node in a container"
    (let [node *node*]
      (is (not (nil? node)))
      (xt/submit-tx node [[:put-docs :users {:xt/id "test-user" :name "Test User"}]])
      (let [result (xt/q node '(->
                                 (from :users [*])
                                 (where (= xt/id "test-user"))))]
        (is (= "Test User" (:name (first result))))))))

(deftest xtdb-math-functions-demo
  (testing "XTDB 2.x math functions in queries"
    (let [node *node*
          tx (xt/submit-tx node [[:put-docs :math-test {:xt/id 1 :x 0.5}]])]
      (Thread/sleep 1000)
      (testing "sqrt in where"
        (let [result (xt/q node '(from :math-test [x] (where (> (sqrt x) 0))))]
          (is (= 1 (count result)))
          (is (= 0.5 (:x (first result))))))
      (testing "asin in where"
        (let [result (xt/q node '(from :math-test [x] (where (> (asin x) 0))))]
          (is (= 1 (count result)))
          (is (= 0.5 (:x (first result))))))
      (testing "sin/cos in where"
        (let [result (xt/q node '(from :math-test [x] (where (and (> (sin x) 0) (< (cos x) 1)))))]
          (is (= 1 (count result)))
          (is (= 0.5 (:x (first result)))))))))
