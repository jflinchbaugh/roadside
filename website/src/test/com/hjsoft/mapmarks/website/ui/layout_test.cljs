(ns com.hjsoft.mapmarks.website.ui.layout-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [helix.core :refer [$]]
            ["@testing-library/react" :as tlr]
            [com.hjsoft.mapmarks.website.ui.layout :as layout]
            [com.hjsoft.mapmarks.website.state :as state]
            [com.hjsoft.mapmarks.website.config :as config]
            [goog.object :as gobj]))

;; Automatically unmount components after each test
(use-fixtures :each
  {:after tlr/cleanup})

(deftest notification-toast-test
  (is (some? js/document) "js/document should be defined")
  (testing "no notification message when message is not present"
    (let [ctx state/app-context
          res (tlr/render
               ($ (gobj/get ctx "Provider")
                  {:value {:state {:notification nil}
                           :dispatch (fn [_])}}
                  ($ layout/notification-toast)))
          container (.-container res)]
      (is (= "" (.-textContent container)))))

  (testing "renders notification message when present"
    (let [ctx state/app-context
          test-notification {:type :success :message "Test Success Message"}
          res (tlr/render
               ($ (gobj/get ctx "Provider")
                  {:value {:state {:notification test-notification}
                           :dispatch (fn [_])}}
                  ($ layout/notification-toast)))
          container (.-container res)
          toast (.querySelector container ".notification-toast.success")]
      (is (some? toast) "The toast element should exist")
      (is (= (.-textContent toast) "Test Success Message")
        "success message should be seen"))))

(deftest header-test
  (testing "renders header with title"
    (let [ctx state/app-context
          res (tlr/render
               ($ (gobj/get ctx "Provider")
                  {:value {:state {:config {:app-name "MapMarks"
                                            :app-logo "\uD83D\uDCCD"
                                            :tags-name-article "a"
                                            :mark-name-article "a"}}
                           :ui {:set-show-about-dialog (fn [_])}}}
                  ($ layout/header)))
          container (.-container res)
          title (tlr/getByText container "MapMarks")]
        (is (some? title) "Should render the main header title")))

  (testing "clicking title or image logo clears selected mark"
    (let [ctx state/app-context
          dispatched (atom [])
          res (tlr/render
               ($ (gobj/get ctx "Provider")
                  {:value {:state {:config {:app-name "MapMarks"
                                            :app-logo "logo.png"
                                            :tags-name-article "a"
                                            :mark-name-article "a"}}
                           :dispatch #(swap! dispatched conj %)
                           :ui {:set-show-about-dialog (fn [_])}}}
                  ($ layout/header)))
          container (.-container res)
          title (tlr/getByText container "MapMarks")
          logo (.querySelector container ".logo")]
      (tlr/fireEvent.click title)
      (is (= [[:set-selected-mark nil]] @dispatched))
      (reset! dispatched [])
      (tlr/fireEvent.click logo)
      (is (= [[:set-selected-mark nil]] @dispatched))))

  (testing "clicking text logo clears selected mark"
    (let [ctx state/app-context
          dispatched (atom [])
          res (tlr/render
               ($ (gobj/get ctx "Provider")
                  {:value {:state {:config {:app-name "MapMarks"
                                            :app-logo "📍"
                                            :tags-name-article "a"
                                            :mark-name-article "a"}}
                           :dispatch #(swap! dispatched conj %)
                           :ui {:set-show-about-dialog (fn [_])}}}
                  ($ layout/header)))
          container (.-container res)
          logo (.querySelector container ".logo")]
      (tlr/fireEvent.click logo)
      (is (= [[:set-selected-mark nil]] @dispatched)))))

(deftest config-icon-test
  (testing "config has :app-icon configured"
    (is (some? (:app-icon config/config))
        "config map should contain :app-icon")
    (is (= "images/apples.png" (:app-icon config/config))
        "default :app-icon should be apples.png")))
