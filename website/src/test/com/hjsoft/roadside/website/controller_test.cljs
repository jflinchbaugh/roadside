(ns com.hjsoft.roadside.website.controller-test
  (:require [cljs.test :as t :refer [deftest is testing]]
            [init]
            [com.hjsoft.roadside.website.controller :as sut]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.domain.stand :as stand-domain]))

(deftest create-stand-notification-test
  (testing "System notification is triggered when name and products are missing"
    (reset! init/notification-calls [])
    (let [app-state state/initial-app-state
          dispatch (fn [_])
          form-data (assoc stand-domain/default-stand-form-data
                           :name ""
                           :products [])]
      ;; Use with-redefs to prevent remote calls
      (with-redefs [sut/remote-create-stand! (fn [_ _ _])]
        (sut/create-stand! app-state dispatch form-data))
      
      (let [calls @init/notification-calls]
        (is (= 1 (count calls)) "Notification should have been called")
        (is (= "Stand Added" (:title (first calls))))
        (is (clojure.string/includes? (get-in (first calls) [:options :body]) "Remember to complete")))))

  (testing "System notification is NOT triggered when name is provided"
    (reset! init/notification-calls [])
    (let [app-state state/initial-app-state
          dispatch (fn [_])
          form-data (assoc stand-domain/default-stand-form-data
                           :name "My New Stand"
                           :products [])]
      (with-redefs [sut/remote-create-stand! (fn [_ _ _])]
        (sut/create-stand! app-state dispatch form-data))
      
      (is (= 0 (count @init/notification-calls)) "No notification if name provided")))

  (testing "System notification is NOT triggered when products are provided"
    (reset! init/notification-calls [])
    (let [app-state state/initial-app-state
          dispatch (fn [_])
          form-data (assoc stand-domain/default-stand-form-data
                           :name ""
                           :products ["Apples"])]
      (with-redefs [sut/remote-create-stand! (fn [_ _ _])]
        (sut/create-stand! app-state dispatch form-data))
      
      (is (= 0 (count @init/notification-calls)) "No notification if products provided"))))
