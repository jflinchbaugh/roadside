(ns com.hjsoft.roadside.website.ui.export-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [helix.core :refer [$]]
            ["@testing-library/react" :as tlr]
            [com.hjsoft.roadside.website.ui.forms.export :as export]
            [com.hjsoft.roadside.website.state :as state]
            [goog.object :as gobj]
            ["react" :as react]
            [clojure.string :as str]))

(use-fixtures :each
  {:after tlr/cleanup})

(defn render-with-context [component context-val]
  (let [app-ctx state/app-context]
    (tlr/render
     (react/createElement (gobj/get app-ctx "Provider")
                          #js {:value context-val}
                          component))))

(deftest export-dialog-test
  (testing "export-dialog displays correct links"
    (let [closed (atom false)
          context-val {:state {}
                       :dispatch (fn [_])
                       :ui {:set-show-export-dialog
                            (fn [v] (when (false? v) (reset! @closed true)))}}
          res (render-with-context ($ export/export-dialog) context-val)
          container (.-container res)]

      (testing "displays KML feed URL"
        (let [kml-label (tlr/getByText container "KML Feed (Live):")
              kml-input (tlr/getByDisplayValue
                          container
                          (re-pattern
                            (str
                              (.. js/window -location -origin)
                              ".*/api/stands.kml")))]
          (is (some? kml-label))
          (is (some? kml-input))))

      (testing "displays CSV download link"
        (let [csv-link (tlr/getByText container "Download CSV")]
          (is (some? csv-link))
          (is (str/includes? (.-href csv-link) "/api/stands.csv")))))))
