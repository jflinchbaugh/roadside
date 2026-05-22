(ns com.hjsoft.mapmarks.website.api-test
  (:require [cljs.test :refer [deftest is testing async]]
            [com.hjsoft.mapmarks.website.api :as sut]
            [cljs.core.async :refer [go <! put! chan]]))

(defn- mock-http-response [response]
  (let [c (chan)]
    (put! c response)
    c))

(defn- make-mock-deps [method-key handler]
  (assoc sut/default-http-deps method-key handler))

(deftest fetch-marks-test
  (async done
         (testing "fetch-marks constructs correct URL and auth"
           (let [deps (make-mock-deps
                       :get (fn [url opts]
                              (is (= "s/test-site/api/marks" url))
                              (is (= {:username "user" :password "pass"}
                                     (:basic-auth opts)))
                              (is (= {:lat 1.0 :lon 2.0 :since "2026-03-21T12:00:00Z"}
                                     (:query-params opts)))
                              (mock-http-response
                               {:success true
                                :body {:marks [{:id 1}]
                                       :deleted-ids []
                                       :new-sync "2026-03-21T13:00:00Z"}})))]
             (go
               (let [result (<! (sut/fetch-marks "test-site" "user" "pass" 1.0 2.0 "2026-03-21T12:00:00Z" deps))]
                 (is (:success result))
                 (is (= {:marks [{:id 1}]
                         :deleted-ids []
                         :new-sync "2026-03-21T13:00:00Z"}
                        (:data result)))
                 (done)))))))

(deftest fetch-marks-failure-test
  (async done
         (testing "fetch-marks handles HTTP error"
           (let [deps (make-mock-deps
                       :get (fn [_ _]
                              (mock-http-response
                               {:success false
                                :status 500
                                :status-text "Internal Server Error"})))]
             (go
               (let [result (<! (sut/fetch-marks "test-site" "user" "pass" 1.0 2.0 nil deps))]
                 (is (not (:success result)))
                 (is (= ["Internal Server Error"] (:error result)))
                 (done)))))))

(deftest create-mark-test
  (async done
         (testing "create-mark sends POST with correct body"
           (let [mark {:name "New Mark"}
                 deps (make-mock-deps
                       :post (fn [url opts]
                               (is (= "s/test-site/api/marks" url))
                               (is (= (assoc mark :site "test-site") (:json-params opts)))
                               (mock-http-response
                                {:success true
                                 :body {:id "new-id"}})))]
             (go
               (let [result (<! (sut/create-mark "test-site" "user" "pass" mark deps))]
                 (is (:success result))
                 (is (= "new-id" (get-in result [:data :id])))
                 (done)))))))

(deftest update-mark-test
  (async done
         (testing "update-mark sends PUT to specific resource"
           (let [mark {:id "s123" :name "Updated"}
                 deps (make-mock-deps
                       :put (fn [url opts]
                              (is (= "s/test-site/api/marks/s123" url))
                              (is (= (assoc mark :site "test-site") (:json-params opts)))
                              (mock-http-response {:success true
                                                   :body mark})))]
             (go
               (let [result (<! (sut/update-mark "test-site" "user" "pass" mark deps))]
                 (is (:success result))
                 (is (= "Updated" (get-in result [:data :name])))
                 (done)))))))

(deftest delete-mark-test
  (async done
         (testing "delete-mark sends DELETE"
           (let [deps (make-mock-deps
                       :delete (fn [url opts]
                                 (is (= "s/test-site/api/marks/s123" url))
                                 (mock-http-response {:success true})))]
             (go
               (let [result (<! (sut/delete-mark "test-site" "user" "pass" "s123" deps))]
                 (is (:success result))
                 (done)))))))

(deftest geocode-address-test
  (async done
         (testing "geocode-address handles successful lookup"
           (let [deps (make-mock-deps
                       :get (fn [url opts]
                              (is (= "s/test-site/api/geocode" url))
                              (is (= {:q "Lancaster, PA"} (:query-params opts)))
                              (mock-http-response {:success true
                                                   :body [{:lat "40.0" :lon "-76.0"}]})))]
             (go
               (let [result (<! (sut/geocode-address "test-site" "user" "pass" "Lancaster, PA" deps))]
                 (is (:success result))
                 (is (= 40.0 (:lat result)))
                 (is (= -76.0 (:lng result)))
                 (done)))))))

(deftest geocode-failure-test
  (async done
         (testing "geocode-address handles not found"
           (let [deps (make-mock-deps
                       :get (fn [_ _]
                              (mock-http-response {:success true :body [] :status-text "Not found"})))]
             (go
               (let [result (<! (sut/geocode-address "test-site" "user" "pass" "Non-existent" deps))]
                 (is (not (:success result)))
                 (is (= "Not found" (:error result)))
                 (done)))))))

(deftest reverse-geocode-test
  (async done
         (testing "reverse-geocode sends correct params"
           (let [deps (make-mock-deps
                       :get (fn [url opts]
                              (is (= "s/test-site/api/reverse-geocode" url))
                              (is (= {:lat 40.0 :lon -76.0} (:query-params opts)))
                              (mock-http-response {:success true :body {:address {:road "Main St"}}})))]
             (go
               (let [result (<! (sut/reverse-geocode "test-site" "user" "pass" 40.0 -76.0 deps))]
                 (is (:success result))
                 (is (= "Main St" (get-in result [:data :address :road])))
                 (done)))))))

(deftest vote-mark-test
  (async done
         (testing "vote-mark sends POST to vote endpoint"
           (let [deps (make-mock-deps
                       :post (fn [url opts]
                               (is (= "s/test-site/api/marks/m123/vote" url))
                               (is (= {:value 1} (:json-params opts)))
                               (mock-http-response {:success true})))]
             (go
               (let [result (<! (sut/vote-mark "test-site" "user" "pass" "m123" 1 deps))]
                 (is (:success result))
                 (done)))))))

(deftest register-user-test
  (async done
         (testing "register-user sends POST to register endpoint"
           (let [deps (make-mock-deps
                       :post (fn [url opts]
                               (is (= "s/test-site/api/register" url))
                               (is (= {:login "user" :password "pass" :email "a@b.c" :site "test-site"} (:form-params opts)))
                               (mock-http-response {:status 201 :body {:login "user"}})))]
             (go
               (let [result (<! (sut/register-user "test-site" "user" "pass" "a@b.c" deps))]
                 (is (:success result))
                 (is (= "user" (get-in result [:data :login])))
                 (done)))))))
