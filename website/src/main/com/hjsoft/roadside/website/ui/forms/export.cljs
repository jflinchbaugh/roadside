(ns com.hjsoft.roadside.website.ui.forms.export
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.ui.hooks :as ui-hooks]
            [com.hjsoft.roadside.website.utils :as utils]))

(defnc export-dialog []
  (let [{:keys [set-show-export-dialog]} (state/use-ui)
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
       (d/h3 "Google Maps Integration")
       (d/button
        {:class "button icon-button"
         :onClick #(set-show-export-dialog false)
         :title "Close"}
        "\u2715"))
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
          (let [kml-url (str base-url "api/stands.kml")]
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
          (let [rss-url (str base-url "api/stands.rss")]
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
            :href (str base-url "api/stands.kml")
            :download "stands.kml"}
           "Download KML"))
         (d/div
          {:class "export-link-item"}
          (d/label "CSV for Import:")
          (d/a
           {:class "download-link"
            :href "api/stands.csv" ;; relative to the current base
            :download "stands.csv"}
           "Download CSV")))))))))
