(ns com.hjsoft.roadside.website.ui.layout-test
  (:require [cljs.test :as t :refer [deftest is testing async]]
            [helix.core :refer [$]]
            ["react-dom/client" :as rdom]
            ["react" :as react]
            [com.hjsoft.roadside.website.ui.layout :as layout]
            [com.hjsoft.roadside.website.state :as state]
            [clojure.string :as str]))

(deftest notification-toast-test
  (testing "renders notification message when present"
    (let [container (.createElement js/document "div")
          ;; We need to append to body for some React features, but div is fine here
          _ (.appendChild (.-body js/document) container)
          root (.createRoot rdom container)
          test-notification {:type :success :message "Test Success Message"}]

      (react/act
       (fn []
         (let [^js ctx state/app-context]
           (.render root
                    ($ (.-Provider ctx)
                       {:value {:state {:notification test-notification}
                                :dispatch (fn [action] (js/console.log "Dispatch:" action))}}
                       ($ layout/notification-toast))))))

      (let [html (.-innerHTML container)]
        (is (str/includes? html "Test Success Message") "Should contain the message")
        (is (str/includes? html "notification-toast") "Should have the toast class")
        (is (str/includes? html "success") "Should have the success class"))

      ;; Cleanup
      (react/act (fn [] (.unmount root)))
      (.removeChild (.-body js/document) container))))
