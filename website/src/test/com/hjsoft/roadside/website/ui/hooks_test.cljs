(ns com.hjsoft.roadside.website.ui.hooks-test
  (:require [cljs.test :refer [deftest is testing]]
            [com.hjsoft.roadside.website.ui.hooks :as sut]
            [com.hjsoft.roadside.website.state :as state]
            [helix.core :refer [$]]
            ["react" :as react]
            ["@testing-library/react" :refer [renderHook act]]))

(deftest use-escape-key-test
  (testing "use-escape-key adds and removes event listener"
    (let [called (atom false)
          on-escape #(reset! called true)
          _ (renderHook (fn [] (sut/use-escape-key on-escape)))]
      (act (fn []
             (.dispatchEvent js/document
                             (js/KeyboardEvent. "keydown" #js {:key "Escape"}))))
      (is (true? @called)))))

(deftest use-user-location-test
  (testing "use-user-location success"
    (let [mock-geo #js {:getCurrentPosition
                        (fn [success-cb _ _]
                          (success-cb #js {:coords #js {:latitude 1.0
                                                       :longitude 2.0}}))}
          dispatch (fn [_])
          hook (renderHook (fn [] (sut/use-user-location dispatch mock-geo)))
          result (.-result hook)]
      (act (fn []
             ((:get-location (aget result "current")))))
      (is (= [1.0 2.0] (:location (aget result "current"))))
      (is (nil? (:error (aget result "current"))))))

  (testing "use-user-location failure"
    (let [mock-geo #js {:getCurrentPosition
                        (fn [_ error-cb _]
                          (error-cb #js {:message "Permission denied"}))}
          dispatch (fn [_])
          hook (renderHook (fn [] (sut/use-user-location dispatch mock-geo)))
          result (.-result hook)]
      (act (fn []
             ((:get-location (aget result "current")))))
      (is (nil? (:location (aget result "current"))))
      (is (= "Unable to retrieve location." (:error (aget result "current"))))))

  (testing "use-user-location not supported"
    (let [dispatch (fn [_])
          hook (renderHook (fn [] (sut/use-user-location dispatch nil)))
          result (.-result hook)]
      (act (fn []
             ((:get-location (aget result "current")))))
      (is (nil? (:location (aget result "current"))))
      (is (= "Geolocation not supported." (:error (aget result "current")))))))
