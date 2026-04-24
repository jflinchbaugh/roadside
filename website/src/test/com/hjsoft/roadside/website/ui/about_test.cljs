(ns com.hjsoft.roadside.website.ui.about-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [helix.core :refer [$]]
            ["@testing-library/react" :as tlr]
            [com.hjsoft.roadside.website.ui.forms.about :as about]
            [com.hjsoft.roadside.website.state :as state]
            [goog.object :as gobj]
            ["react" :as react]))

(use-fixtures :each
  {:after tlr/cleanup})

(defn render-with-context [component context-val]
  (let [app-ctx state/app-context]
    (tlr/render
     (react/createElement (gobj/get app-ctx "Provider")
                          #js {:value context-val}
                          component))))

(deftest about-dialog-test
  (testing "about-dialog displays correct information"
    (let [context-val {:ui {:set-show-about-dialog (fn [_])}}
          res (render-with-context ($ about/about-dialog) context-val)
          container (.-container res)]
      (is (some? (tlr/queryByText container "About Roadside Stands"))
          "Header should be visible")
      (is (some? (tlr/queryByText container #"GitHub"))
        "source link")
      (is (some? (tlr/queryByText container #"suggestions"))
          "Copy should be visible")))

  (testing "about-dialog can be closed via X button"
    (let [closed (atom false)
          context-val {:ui {:set-show-about-dialog (fn [v] (when (false? v) (reset! closed true)))}}
          res (render-with-context ($ about/about-dialog) context-val)
          container (.-container res)
          x-btn (tlr/getByTitle container "Close")]
      (tlr/fireEvent.click x-btn)
      (is (true? @closed) "Dialog should be closed"))))
