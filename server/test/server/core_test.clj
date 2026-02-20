(ns server.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [server.core :as core]
            [xtdb.api :as xt]
            [server.xtdb-container :as xtn]
            [clojure.data.json :as json])
  (:import [java.io ByteArrayInputStream]))

(defn with-xtdb-container [f]
  (xtn/with-xtdb-client
    (fn [n]
      (reset! core/node n)
      (f)
      (reset! core/node nil))))

(use-fixtures :once with-xtdb-container)

(deftest ping-test
  (testing "Ping handler returns 200 pong"
    (let [response (core/ping-handler {})]
      (is (= 200 (:status response)))
      (is (= "\"pong\"" (:body response))))))

(deftest register-test
  (testing "Register handler saves user to DB"
    (let [req {:params {:login "alice" :password "secret"}}
          response (core/register-handler req)]
      (is (= 201 (:status response)))
      (let [user (first
                   (xt/q
                     @core/node
                     '(->
                        (from :users [login password])
                        (where (= login "alice")))))]
        (is (= "alice" (:login user)))
        (is (= "secret" (:password user))))))
  (testing "Register handler with a duplicate"
    (let [req {:params {:login "alice" :password "again"}}
          response (core/register-handler req)]
      (is (= 403 (:status response)))
      (let [user (first
                  (xt/q
                   @core/node
                   '(->
                     (from :users [login password])
                     (where (= login "alice")))))]
        (is (= "alice" (:login user)))
        (is (= "secret" (:password user)) "password not touched")))))

(deftest stands-test
  (testing "Stands handlers"
    (let [stand-doc {:name "Morning Coffee" :location "Main St"}
          body (json/write-str stand-doc)
          create-req {:body (ByteArrayInputStream. (.getBytes body))
                      :identity "alice"}
          create-resp (core/create-stand-handler create-req)]
      (is (= 201 (:status create-resp)))
      (let [created-stand (json/read-str (:body create-resp) :key-fn keyword)
            id (:id created-stand)]
        (is (not (nil? id)))
        (is (= "Morning Coffee" (:name created-stand)))

        (testing "Get all stands"
          (let [get-resp (core/get-stands-handler {})]
            (is (= 200 (:status get-resp)))
            (is (= 1 (count (json/read-str (:body get-resp) :key-fn keyword))))))

        (testing "Get single stand"
          (let [get-resp (core/get-stand-handler {:path-params {:id id}})]
            (is (= 200 (:status get-resp)))
            (is (= "Morning Coffee" (:name (json/read-str (:body get-resp) :key-fn keyword))))))

        (testing "Update stand"
          (let [update-doc (assoc created-stand :name "Evening Coffee")
                update-body (json/write-str update-doc)
                update-req {:path-params {:id id}
                            :body (ByteArrayInputStream. (.getBytes update-body))}
                update-resp (core/update-stand-handler update-req)]
            (is (= 200 (:status update-resp)))
            (is (= "Evening Coffee" (:name (json/read-str (:body update-resp) :key-fn keyword))))))

        (testing "Delete stand"
          (let [del-resp (core/delete-stand-handler {:path-params {:id id}})]
            (is (= 200 (:status del-resp)))
            (let [del-check (xt/q
                             @core/node
                             ['(fn [id]
                                 (->
                                  (from :stands [xt/id])
                                  (where (= xt/id id))))
                              id])]
              (is (= 0 (count del-check))))))))))

(deftest auth-test
  (testing "my-authfn"
    (xt/submit-tx @core/node [[:put-docs :users {:xt/id "u1" :login "bob" :password "pass"}]])
    (is (= "bob" (core/my-authfn {} {:username "bob" :password "pass"})))
    (is (nil? (core/my-authfn {} {:username "bob" :password "wrong"})))))
