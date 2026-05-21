(ns com.hjsoft.mapmarks.website.ui.forms.about
  (:require [helix.core :refer [defnc $]]
            [helix.dom :as d]
            [clojure.string :as str]
            [com.hjsoft.mapmarks.website.state :as state]
            [com.hjsoft.mapmarks.website.ui.hooks :as ui-hooks]
            [com.hjsoft.mapmarks.website.ui.forms.buttons :refer [close-button]]))

(defnc about-dialog []
  (let [app-state (state/use-app-state)
        config (:config app-state)
        {:keys [set-show-about-dialog]} (state/use-ui)]
    (ui-hooks/use-escape-key #(set-show-about-dialog false))
    (d/div
     {:class "dialog-overlay"
      :onClick #(set-show-about-dialog false)}
     (d/div
      {:class "about-dialog"
       :onClick #(.stopPropagation %)}
      (d/div
       {:class "about-header"}
       (d/h3 (str "About " (:app-name config)))
       ($ close-button {:onClick #(set-show-about-dialog false)}))
      (d/div
       {:class "about-content"}
       (d/p
         (str "Lots of places are too small to be on the big maps,
          but this is your map to add, share, and find
          those " (str/lower-case (:mark-name-plural config)) "."))
       (d/ul
         (d/li "Create an account in settings.")
         (d/li (str "Place "
                    (str/lower-case (:mark-name-plural config))
                    " and their "
                    (str/lower-case (:tags-name-plural config)) " on the map."))
         (d/li (str "Find your favorite " (str/lower-case (:mark-name-plural config)) ".")))
       (d/p
        "Feedback and suggestions are welcome. "
        (d/a {:href (str "mailto:" (:feedback-email config))}
             (:feedback-email config)))
       (d/p
         "Source on "
         (d/a {:href "https://github.com/jflinchbaugh/roadside/"
               :target "_blank"} "GitHub")
          "."))))))
