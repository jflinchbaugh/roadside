(ns com.hjsoft.mapmarks.website.ui.hooks-test
  (:require [cljs.test :refer [deftest is testing]]
            [com.hjsoft.mapmarks.website.ui.hooks :as sut]
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
  (testing "use-user-location success with getCurrentPosition fallback"
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

  (testing "use-user-location continuous updates with watchPosition"
    (let [pos-cb (atom nil)
          cleared-id (atom nil)
          mock-geo #js {:watchPosition
                        (fn [success-cb _ _]
                          (reset! pos-cb success-cb)
                          101)
                        :clearWatch
                        (fn [id]
                          (reset! cleared-id id))}
          dispatch (fn [_])
          hook (renderHook (fn [] (sut/use-user-location dispatch mock-geo)))
          result (.-result hook)]
      (act (fn []
             ((:get-location (aget result "current")))))
      (is (some? @pos-cb) "watchPosition callback should be registered")
      (act (fn []
             (@pos-cb #js {:coords #js {:latitude 10.0 :longitude 20.0}})))
      (is (= [10.0 20.0] (:location (aget result "current"))))
      ;; Simulate next location tick
      (act (fn []
             (@pos-cb #js {:coords #js {:latitude 10.5 :longitude 20.5}})))
      (is (= [10.5 20.5] (:location (aget result "current"))))
      ;; Cancel location clears the watch
      (act (fn []
             ((:cancel-location (aget result "current")))))
      (is (= 101 @cleared-id) "clearWatch should be called with watch ID")))

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
      (is (= "Unable to retrieve location: Permission denied"
             (:error (aget result "current"))))))

  (testing "use-user-location not supported"
    (let [dispatch (fn [_])
          hook (renderHook (fn [] (sut/use-user-location dispatch nil)))
          result (.-result hook)]
      (act (fn []
             ((:get-location (aget result "current")))))
      (is (nil? (:location (aget result "current"))))
      (is (= "Geolocation not supported." (:error (aget result "current")))))))
