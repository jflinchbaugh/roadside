(ns com.hjsoft.roadside.website.api-test
  (:require [cljs.test :refer [deftest is testing async]]
            [com.hjsoft.roadside.website.api :as sut]
            [cljs.core.async :refer [go <! put! chan]]))

(defn- mock-http-response [response]
  (let [c (chan)]
    (put! c response)
    c))

(defn- make-mock-deps [method-key handler]
  (assoc sut/default-http-deps method-key handler))

(deftest fetch-stands-test
  (async done
         (testing "fetch-stands constructs correct URL and auth"
           (let [deps (make-mock-deps
                       :get (fn [url opts]
                              (is (= "api/stands" url))
                              (is (= {:username "user" :password "pass"}
                                     (:basic-auth opts)))
                              (is (= {:lat 1.0 :lon 2.0 :since "2026-03-21T12:00:00Z"}
                                     (:query-params opts)))
                              (mock-http-response
                               {:success true
                                :body {:stands [{:id 1}]
                                       :deleted-ids []
                                       :new-sync "2026-03-21T13:00:00Z"}})))]
             (go
               (let [result (<! (sut/fetch-stands "user" "pass" 1.0 2.0 "2026-03-21T12:00:00Z" deps))]
                 (is (:success result))
                 (is (= {:stands [{:id 1}]
                         :deleted-ids []
                         :new-sync "2026-03-21T13:00:00Z"}
                        (:data result)))
                 (done)))))))

(deftest fetch-stands-failure-test
  (async done
         (testing "fetch-stands handles HTTP error"
           (let [deps (make-mock-deps
                       :get (fn [_ _]
                              (mock-http-response
                               {:success false
                                :status 500
                                :status-text "Internal Server Error"})))]
             (go
               (let [result (<! (sut/fetch-stands "user" "pass" 1.0 2.0 nil deps))]
                 (is (not (:success result)))
                 (is (= ["Internal Server Error"] (:error result)))
                 (done)))))))

(deftest create-stand-test
  (async done
         (testing "create-stand sends POST with correct body"
           (let [stand {:name "New Stand"}
                 deps (make-mock-deps
                       :post (fn [url opts]
                               (is (= "api/stands" url))
                               (is (= stand (:json-params opts)))
                               (mock-http-response
                                {:success true
                                 :body {:id "new-id"}})))]
             (go
               (let [result (<! (sut/create-stand "user" "pass" stand deps))]
                 (is (:success result))
                 (is (= "new-id" (get-in result [:data :id])))
                 (done)))))))

(deftest update-stand-test
  (async done
         (testing "update-stand sends PUT to specific resource"
           (let [stand {:id "s123" :name "Updated"}
                 deps (make-mock-deps
                       :put (fn [url opts]
                              (is (= "api/stands/s123" url))
                              (is (= stand (:json-params opts)))
                              (mock-http-response {:success true
                                                   :body stand})))]
             (go
               (let [result (<! (sut/update-stand "user" "pass" stand deps))]
                 (is (:success result))
                 (is (= "Updated" (get-in result [:data :name])))
                 (done)))))))

(deftest delete-stand-test
  (async done
         (testing "delete-stand sends DELETE"
           (let [deps (make-mock-deps
                       :delete (fn [url opts]
                                 (is (= "api/stands/s123" url))
                                 (mock-http-response {:success true})))]
             (go
               (let [result (<! (sut/delete-stand "user" "pass" "s123" deps))]
                 (is (:success result))
                 (done)))))))

(deftest geocode-address-test
  (async done
         (testing "geocode-address handles successful lookup"
           (let [deps (make-mock-deps
                       :get (fn [url opts]
                              (is (= "api/geocode" url))
                              (is (= "123 Main"
                                     (get-in opts [:query-params :q])))
                              (mock-http-response
                               {:success true
                                :body [{:lat "40.0" :lon "-76.0"}]})))]
             (go
               (let [result (<! (sut/geocode-address
                                 "user"
                                 "pass"
                                 "123 Main"
                                 deps))]
                 (is (:success result))
                 (is (= 40.0 (:lat result)))
                 (is (= -76.0 (:lng result)))
                 (done)))))))

(deftest reverse-geocode-test
  (async done
         (testing "reverse-geocode handles successful lookup"
           (let [deps (make-mock-deps
                       :get (fn [url opts]
                              (is (= "api/reverse-geocode" url))
                              (is (= 40.0 (get-in opts [:query-params :lat])))
                              (mock-http-response
                               {:success true
                                :body {:address {:road "Main"}}})))]
             (go
               (let [result (<! (sut/reverse-geocode
                                 "user"
                                 "pass"
                                 40.0
                                 -76.0
                                 deps))]
                 (is (:success result))
                 (is (= "Main" (get-in result [:data :address :road])))
                 (done)))))))

(deftest register-user-test
  (async done
         (testing "register-user sends form params"
           (let [deps (make-mock-deps
                       :post (fn [url opts]
                               (is (= "api/register" url))
                               (is (= "u" (get-in opts [:form-params :login])))
                               (mock-http-response
                                {:status 201
                                 :body {:msg "ok"}})))]
             (go
               (let [result (<! (sut/register-user "u" "p" "e" deps))]
                 (is (:success result))
                 (is (= "ok" (get-in result [:data :msg])))
                 (done)))))))

(deftest register-user-failure-message-test
  (async done
         (testing "register-user handles server error with message"
           (let [deps (make-mock-deps
                       :post (fn [_ _]
                               (mock-http-response
                                {:status 400
                                 :body {:message "User exists"}})))]
             (go
               (let [result (<! (sut/register-user "u" "p" "e" deps))]
                 (is (not (:success result)))
                 (is (= ["User exists"] (:error result)))
                 (done)))))))

(deftest register-user-failure-errors-test
  (async done
         (testing "register-user handles server error with list of errors"
           (let [deps (make-mock-deps
                       :post (fn [_ _]
                               (mock-http-response
                                {:status 400
                                 :body {:errors ["Error 1" "Error 2"]}})))]
             (go
               (let [result (<! (sut/register-user "u" "p" "e" deps))]
                 (is (not (:success result)))
                 (is (= ["Error 1" "Error 2"] (:error result)))
                 (done)))))))

(deftest register-user-failure-generic-test
  (async done
         (testing "register-user handles generic server error"
           (let [deps (make-mock-deps
                       :post (fn [_ _]
                               (mock-http-response
                                {:status 500
                                 :status-text "Internal Server Error"})))]
             (go
               (let [result (<! (sut/register-user "u" "p" "e" deps))]
                 (is (not (:success result)))
                 (is (= ["Internal Server Error"] (:error result)))
                 (done)))))))
