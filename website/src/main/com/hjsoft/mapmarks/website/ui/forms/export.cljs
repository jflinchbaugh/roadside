(ns com.hjsoft.mapmarks.website.ui.forms.export
  (:require [helix.core :refer [defnc $]]
            [helix.dom :as d]
            [com.hjsoft.mapmarks.website.state :as state]
            [com.hjsoft.mapmarks.website.ui.hooks :as ui-hooks]
            [com.hjsoft.mapmarks.website.utils :as utils]
            [com.hjsoft.mapmarks.website.ui.forms.buttons :refer [close-button]]))

(defnc export-dialog []
  (let [{:keys [set-show-export-dialog]} (state/use-ui)
        {:keys [config]} (state/use-app-state)
        site (:site config)
        base-url (utils/get-app-base-url)]
    (ui-hooks/use-escape-key #(set-show-export-dialog false))
    (d/div
     {:class "dialog-overlay"
      :onClick #(set-show-export-dialog false)}
     (d/div
      {:class "settings-dialog"
       :onClick #(.stopPropagation %)}
      (d/div
       {:class "settings-header"}
       (d/h3 "External Integration")
       ($ close-button {:onClick #(set-show-export-dialog false)}))
      (d/div
       {:class "settings-content"}
       (d/div
        {:class "export-section"
         :style {:margin-top 0 :border-top "none"}}
        (d/div
         {:class "export-links"}
         (d/div
          {:class "export-link-item"}
          (d/label "KML Feed (Live):")
          (let [kml-url (str base-url "s/" site "/feed.kml")]
            (d/div
             {:class "export-url-container"}
             (d/input
              {:class "export-url-input"
               :value kml-url
               :readOnly true})
             (d/button
              {:class "copy-btn"
               :onClick #(utils/copy-to-clipboard! kml-url)}
              "Copy"))))
         (d/div
          {:class "export-link-item"}
          (d/label "RSS Feed (Live):")
          (let [rss-url (str base-url "s/" site "/feed.rss")]
            (d/div
             {:class "export-url-container"}
             (d/input
              {:class "export-url-input"
               :value rss-url
               :readOnly true})
             (d/button
              {:class "copy-btn"
               :onClick #(utils/copy-to-clipboard! rss-url)}
              "Copy"))))
         (d/div
          {:class "export-link-item"}
          (d/label "KML for Import:")
          (d/a
           {:class "download-link"
            :href (str base-url "s/" site "/feed.kml")
            :download "feed.kml"}
           "Download KML"))
         (d/div
          {:class "export-link-item"}
          (d/label "CSV for Import:")
          (d/a
           {:class "download-link"
            :href (str base-url "s/" site "/feed.csv")
            :download "feed.csv"}
           "Download CSV")))))))))
