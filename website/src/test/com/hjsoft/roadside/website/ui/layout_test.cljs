(ns com.hjsoft.roadside.website.ui.layout-test
  (:require [cljs.test :as t :refer [deftest is testing use-fixtures]]
            [helix.core :refer [$]]
            ["@testing-library/react" :as tlr]
            [com.hjsoft.roadside.website.ui.layout :as layout]
            [com.hjsoft.roadside.website.state :as state]
            [clojure.string :as str]))

;; Automatically unmount components after each test
(use-fixtures :each
  {:after tlr/cleanup})

(deftest notification-toast-test
  (is (some? js/document) "js/document should be defined")
  (testing "no notification message when message is not present"
    (let [^js ctx state/app-context
          res (tlr/render
               ($ (.-Provider ctx)
                  {:value {:state {:notification nil}
                           :dispatch (fn [_])}}
                  ($ layout/notification-toast)))
          container (.-container res)
          toast (.querySelector container ".notification-toast")]
      (is (= "" (.-innerText container)))))

  (testing "renders notification message when present"
    (let [^js ctx state/app-context
          test-notification {:type :success :message "Test Success Message"}
          res (tlr/render
               ($ (.-Provider ctx)
                  {:value {:state {:notification test-notification}
                           :dispatch (fn [_])}}
                  ($ layout/notification-toast)))
          container (.-container res)
          toast (.querySelector container ".notification-toast.success")]
      (is (some? toast) "The toast element should exist")
      (is (= (.-innerText toast) "Test Success Message")
        "success message should be seen"))))

(deftest header-test
  (testing "renders header with title"
    (let [res (tlr/render ($ layout/header))
          container (.-container res)
          title (tlr/getByText container "Roadside Stands")]
        (is (some? title) "Should render the main header title"))))
