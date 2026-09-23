(ns com.hjsoft.mapmarks.website.ui.about-test
  (:require [cljs.test :as t]
            [helix.core :refer [$]]
            ["@testing-library/react" :as tlr]
            [com.hjsoft.mapmarks.website.ui.forms.about :as about]
            [com.hjsoft.mapmarks.website.state :as state]
            [goog.object :as gobj]
            ["react" :as react]))

(t/use-fixtures :each
  {:after tlr/cleanup})

(defn render-with-context [component context-val]
  (let [app-ctx state/app-context]
    (tlr/render
     (react/createElement (gobj/get app-ctx "Provider")
                          #js {:value context-val}
                          component))))

(t/deftest about-dialog-test
  (t/testing "about-dialog displays correct information"
    (let [context-val {:state {:config {:app-name "MapMarks"
                                        :mark-name-singular "Mark"
                                        :mark-name-plural "Marks"
                                        :mark-name-article "a"
                                        :tags-name-plural "Tags"
                                        :tags-name-article "a"}}
                       :ui {:set-show-about-dialog (fn [_])}}
          res (render-with-context ($ about/about-dialog) context-val)
          container (.-container res)]
      (t/is (some? (tlr/queryByText container "About MapMarks"))
            "Header should be visible")
      (t/is (some? (tlr/queryByText container #"GitHub"))
            "source link")
      (t/is (some? (tlr/queryByText container #"suggestions"))
            "Copy should be visible")))

  (t/testing "about-dialog can be closed via X button"
    (let [closed (atom false)
          context-val {:state {:config {:app-name "MapMarks"
                                        :mark-name-singular "Mark"
                                        :mark-name-plural "Marks"
                                        :tags-name-plural "Tags"}}
                       :ui {:set-show-about-dialog (fn [v] (when (false? v) (reset! closed true)))}}
          res (render-with-context ($ about/about-dialog) context-val)
          container (.-container res)
          x-btn (tlr/getByTitle container "Close")]
      (tlr/fireEvent.click x-btn)
      (t/is (true? @closed) "Dialog should be closed"))))
