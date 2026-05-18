(ns server.db-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [server.db :as db]
            [server.xtdb-container :as xtn]
            [xtdb.api :as xt]
            [com.hjsoft.mapmarks.common.utils :as common-utils]))

(defn with-db [f]
  (xtn/with-xtdb-client
    (fn [n]
      (reset! db/node n)
      (f))))

(use-fixtures :each with-db)

(deftest list-marks-duplication-test
  (let [user-id "test-user"
        mark-id "test-mark"]
    ;; Setup data: one user with two records, and one mark
    (xt/execute-tx @db/node
                   [[:put-docs :users {:xt/id "u1" :login user-id :enabled? true}]
                    [:put-docs :users {:xt/id "u2" :login user-id :enabled? true}]
                    [:put-docs :marks {:xt/id mark-id
                                        :creator user-id
                                        :name "Test Mark"
                                        :lat 40.0 :lon -76.0
                                        :shared? true
                                        :updated (common-utils/get-current-timestamp)}]])

    (testing "list-marks (default) should not produce duplicate entries"
      (let [marks (db/list-marks user-id)]
        (is (= 1 (count marks))
            (str "Expected 1 mark, got " (count marks)))))

    (testing "list-marks (with location) should not produce duplicate entries"
      (let [marks (db/list-marks user-id {:lat 40.0 :lon -76.0 :radius 10.0})]
        (is (= 1 (count marks))
            (str "Expected 1 mark in radius, got " (count marks)))))))
