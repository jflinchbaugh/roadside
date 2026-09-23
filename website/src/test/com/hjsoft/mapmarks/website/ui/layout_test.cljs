(ns com.hjsoft.mapmarks.website.ui.layout-test
  (:require [cljs.test :as t]
            [helix.core :refer [$]]
            ["@testing-library/react" :as tlr]
            [com.hjsoft.mapmarks.website.ui.layout :as layout]
            [com.hjsoft.mapmarks.website.state :as state]
            [com.hjsoft.mapmarks.website.config :as config]
            [goog.object :as gobj]))

;; Automatically unmount components after each test
(t/use-fixtures :each
  {:after tlr/cleanup})

(t/deftest notification-toast-test
  (t/is (some? js/document) "js/document should be defined")
  (t/testing "no notification message when message is not present"
    (let [ctx state/app-context
          res (tlr/render
               ($ (gobj/get ctx "Provider")
                  {:value {:state {:notification nil}
                           :dispatch (fn [_])}}
                  ($ layout/notification-toast)))
          container (.-container res)]
      (t/is (= "" (.-textContent container)))))

  (t/testing "renders notification message when present"
    (let [ctx state/app-context
          test-notification {:type :success :message "Test Success Message"}
          res (tlr/render
               ($ (gobj/get ctx "Provider")
                  {:value {:state {:notification test-notification}
                           :dispatch (fn [_])}}
                  ($ layout/notification-toast)))
          container (.-container res)
          toast (.querySelector container ".notification-toast.success")]
      (t/is (some? toast) "The toast element should exist")
      (t/is (= (.-textContent toast) "Test Success Message")
        "success message should be seen"))))

(t/deftest header-test
  (t/testing "renders header with title"
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
        (t/is (some? title) "Should render the main header title")))

  (t/testing "clicking title or image logo clears selected mark"
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
      (t/is (= [[:set-selected-mark nil]] @dispatched))
      (reset! dispatched [])
      (tlr/fireEvent.click logo)
      (t/is (= [[:set-selected-mark nil]] @dispatched))))

  (t/testing "clicking text logo clears selected mark"
    (let [ctx state/app-context
          dispatched (atom [])
          res (tlr/render
               ($ (gobj/get ctx "Provider")
                  {:value {:state {:config {:app-name "MapMarks"
                                            :app-logo "\uD83D\uDCCD"
                                            :tags-name-article "a"
                                            :mark-name-article "a"}}
                           :dispatch #(swap! dispatched conj %)
                           :ui {:set-show-about-dialog (fn [_])}}}
                  ($ layout/header)))
          container (.-container res)
          logo (.querySelector container ".logo")]
      (tlr/fireEvent.click logo)
      (t/is (= [[:set-selected-mark nil]] @dispatched)))))

(t/deftest config-icon-test
  (t/testing "config has :app-icon configured"
    (t/is (some? (:app-icon config/config))
        "config map should contain :app-icon")
    (t/is (= "favicon.ico" (:app-icon config/config))
        "default :app-icon should be favicon.ico")))

(t/deftest config-review-interval-test
  (t/testing "config has :review-interval-days configured"
    (t/is (some? (:review-interval-days config/config))
        "config map should contain :review-interval-days")
    (t/is (= 30 (:review-interval-days config/config))
        "default :review-interval-days should be 30")))
