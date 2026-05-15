(ns server.db-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [server.db :as db]
            [server.xtdb-container :as xtn]
            [xtdb.api :as xt]
            [com.hjsoft.roadside.common.utils :as common-utils]))

(defn with-db [f]
  (xtn/with-xtdb-client
    (fn [n]
      (reset! db/node n)
      (f))))

(use-fixtures :each with-db)

(deftest list-stands-duplication-test
  (let [user-id "test-user"
        stand-id "test-stand"]
    ;; Setup data: one user with two records, and one stand
    (xt/execute-tx @db/node
                   [[:put-docs :users {:xt/id "u1" :login user-id :enabled? true}]
                    [:put-docs :users {:xt/id "u2" :login user-id :enabled? true}]
                    [:put-docs :stands {:xt/id stand-id
                                        :creator user-id
                                        :name "Test Stand"
                                        :lat 40.0 :lon -76.0
                                        :shared? true
                                        :updated (common-utils/get-current-timestamp)}]])

    (testing "list-stands (default) should not produce duplicate entries"
      (let [stands (db/list-stands user-id)]
        (is (= 1 (count stands))
            (str "Expected 1 stand, got " (count stands)))))

    (testing "list-stands (with location) should not produce duplicate entries"
      (let [stands (db/list-stands user-id {:lat 40.0 :lon -76.0 :radius 10.0})]
        (is (= 1 (count stands))
            (str "Expected 1 stand in radius, got " (count stands)))))))
