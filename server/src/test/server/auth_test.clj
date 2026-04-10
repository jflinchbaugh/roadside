(ns server.auth-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [server.auth :as auth]
            [server.db :as db]
            [buddy.hashers :as hashers]
            [server.xtdb-container :as xtn]))

(defn- with-db [f]
  (xtn/with-xtdb-client
    (fn [node]
      (reset! db/node node)
      (f))))

(use-fixtures :each with-db)

(deftest authfn-test
  (testing "authfn correctly identifies enabled/disabled users"
    (db/save-user {:xt/id "user1" :login "user1" :password (hashers/derive "pass") :enabled? true})
    (db/save-user {:xt/id "user2" :login "user2" :password (hashers/derive "pass") :enabled? false})
    (db/save-user {:xt/id "user3" :login "user3" :password (hashers/derive "pass")}) ; Legacy user, no flag

    (testing "Enabled user can login"
      (is (= "user1" (#'auth/authfn nil {:username "user1" :password "pass"}))))

    (testing "Disabled user cannot login"
      (is (nil? (#'auth/authfn nil {:username "user2" :password "pass"}))))

    (testing "User without flag can login (default to enabled)"
      (is (= "user3" (#'auth/authfn nil {:username "user3" :password "pass"}))))

    (testing "Wrong password fails even for enabled user"
      (is (nil? (#'auth/authfn nil {:username "user1" :password "wrong-pass"}))))))
