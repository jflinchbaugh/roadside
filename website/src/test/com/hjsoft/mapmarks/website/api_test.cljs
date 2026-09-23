(ns com.hjsoft.mapmarks.website.api-test
  (:require [cljs.test :as t]
            [com.hjsoft.mapmarks.website.api :as sut]
            [cljs.core.async :refer [go <! put! chan]]))

(defn- mock-http-response [response]
  (let [c (chan)]
    (put! c response)
    c))

(defn- make-mock-deps [method-key handler]
  (assoc sut/default-http-deps method-key handler))

(t/deftest fetch-marks-test
  (t/async done
         (t/testing "fetch-marks constructs correct URL and auth"
           (let [deps (make-mock-deps
                       :get (fn [url opts]
                              (t/is (= "s/test-site/api/marks" url))
                              (t/is (= {:username "user" :password "pass"}
                                     (:basic-auth opts)))
                              (t/is (= {:lat 1.0 :lon 2.0 :since "2026-03-21T12:00:00Z"}
                                     (:query-params opts)))
                              (mock-http-response
                               {:success true
                                :body {:marks [{:id 1}]
                                       :deleted-ids []
                                       :new-sync "2026-03-21T13:00:00Z"}})))]
             (go
               (let [result (<! (sut/fetch-marks "test-site" "user" "pass" 1.0 2.0 "2026-03-21T12:00:00Z" deps))]
                 (t/is (:success result))
                 (t/is (= {:marks [{:id 1}]
                         :deleted-ids []
                         :new-sync "2026-03-21T13:00:00Z"}
                        (:data result)))
                 (done)))))))

(t/deftest fetch-marks-failure-test
  (t/async done
         (t/testing "fetch-marks handles HTTP error"
           (let [deps (make-mock-deps
                       :get (fn [_ _]
                              (mock-http-response
                               {:success false
                                :status 500
                                :status-text "Internal Server Error"})))]
             (go
               (let [result (<! (sut/fetch-marks "test-site" "user" "pass" 1.0 2.0 nil deps))]
                 (t/is (not (:success result)))
                 (t/is (= ["Internal Server Error"] (:error result)))
                 (done)))))))

(t/deftest create-mark-test
  (t/async done
         (t/testing "create-mark sends POST with correct body"
           (let [mark {:name "New Mark"}
                 deps (make-mock-deps
                       :post (fn [url opts]
                               (t/is (= "s/test-site/api/marks" url))
                               (t/is (= (assoc mark :site "test-site") (:json-params opts)))
                               (mock-http-response
                                {:success true
                                 :body {:id "new-id"}})))]
             (go
               (let [result (<! (sut/create-mark "test-site" "user" "pass" mark deps))]
                 (t/is (:success result))
                 (t/is (= "new-id" (get-in result [:data :id])))
                 (done)))))))

(t/deftest update-mark-test
  (t/async done
         (t/testing "update-mark sends PUT to specific resource"
           (let [mark {:id "s123" :name "Updated"}
                 deps (make-mock-deps
                       :put (fn [url opts]
                              (t/is (= "s/test-site/api/marks/s123" url))
                              (t/is (= (assoc mark :site "test-site") (:json-params opts)))
                              (mock-http-response {:success true
                                                   :body mark})))]
             (go
               (let [result (<! (sut/update-mark "test-site" "user" "pass" mark deps))]
                 (t/is (:success result))
                 (t/is (= "Updated" (get-in result [:data :name])))
                 (done)))))))

(t/deftest delete-mark-test
  (t/async done
         (t/testing "delete-mark sends DELETE"
           (let [deps (make-mock-deps
                       :delete (fn [url opts]
                                 (t/is (= "s/test-site/api/marks/s123" url))
                                 (mock-http-response {:success true})))]
             (go
               (let [result (<! (sut/delete-mark "test-site" "user" "pass" "s123" deps))]
                 (t/is (:success result))
                 (done)))))))

(t/deftest geocode-address-test
  (t/async done
         (t/testing "geocode-address handles successful lookup"
           (let [deps (make-mock-deps
                       :get (fn [url opts]
                              (t/is (= "s/test-site/api/geocode" url))
                              (t/is (= {:q "Lancaster, PA"} (:query-params opts)))
                              (mock-http-response {:success true
                                                   :body [{:lat "40.0" :lon "-76.0"}]})))]
             (go
               (let [result (<! (sut/geocode-address "test-site" "user" "pass" "Lancaster, PA" deps))]
                 (t/is (:success result))
                 (t/is (= 40.0 (:lat result)))
                 (t/is (= -76.0 (:lng result)))
                 (done)))))))

(t/deftest geocode-failure-test
  (t/async done
         (t/testing "geocode-address handles not found"
           (let [deps (make-mock-deps
                       :get (fn [_ _]
                              (mock-http-response {:success true :body [] :status-text "Not found"})))]
             (go
               (let [result (<! (sut/geocode-address "test-site" "user" "pass" "Non-existent" deps))]
                 (t/is (not (:success result)))
                 (t/is (= "Not found" (:error result)))
                 (done)))))))

(t/deftest reverse-geocode-test
  (t/async done
         (t/testing "reverse-geocode sends correct params"
           (let [deps (make-mock-deps
                       :get (fn [url opts]
                              (t/is (= "s/test-site/api/reverse-geocode" url))
                              (t/is (= {:lat 40.0 :lon -76.0} (:query-params opts)))
                              (mock-http-response {:success true :body {:address {:road "Main St"}}})))]
             (go
               (let [result (<! (sut/reverse-geocode "test-site" "user" "pass" 40.0 -76.0 deps))]
                 (t/is (:success result))
                 (t/is (= "Main St" (get-in result [:data :address :road])))
                 (done)))))))

(t/deftest vote-mark-test
  (t/async done
         (t/testing "vote-mark sends POST to vote endpoint"
           (let [deps (make-mock-deps
                       :post (fn [url opts]
                               (t/is (= "s/test-site/api/marks/m123/vote" url))
                               (t/is (= {:value 1} (:json-params opts)))
                               (mock-http-response {:success true})))]
             (go
               (let [result (<! (sut/vote-mark "test-site" "user" "pass" "m123" 1 deps))]
                 (t/is (:success result))
                 (done)))))))

(t/deftest register-user-test
  (t/async done
         (t/testing "register-user sends POST to register endpoint"
           (let [deps (make-mock-deps
                       :post (fn [url opts]
                               (t/is (= "s/test-site/api/register" url))
                               (t/is (= {:login "user" :password "pass" :email "a@b.c" :site "test-site"} (:form-params opts)))
                               (mock-http-response {:status 201 :body {:login "user"}})))]
             (go
               (let [result (<! (sut/register-user "test-site" "user" "pass" "a@b.c" deps))]
                 (t/is (:success result))
                 (t/is (= "user" (get-in result [:data :login])))
                 (done)))))))
