(ns com.hjsoft.mapmarks.website.ui.forms.inputs
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.mapmarks.website.utils :as utils]
            [com.hjsoft.mapmarks.website.state :as state]
            [com.hjsoft.mapmarks.website.ui.map :refer [leaflet-map]]))

(goog-define NODE_TEST false)

(def add-zoom-level 14)

(def icon-reset "\u21BA")
(def icon-location "\u2316")
(def icon-remove "\u2715")

(defnc location-input
  [{:keys
    [mark-form-data
     on-update
     original-coordinate]}]
  (let [app-state (state/use-app-state)
        marks (state/select-marks-by-expiry app-state)
        map-zoom (:map-zoom app-state 11)
        final-zoom (max add-zoom-level map-zoom)
        {:keys [get-location error]} (state/use-user-location-state)]

    (d/div
     {:class "form-group"}
     ($ leaflet-map
        {:div-id "map-form"
         :center (or
                  (utils/parse-coordinates (:coordinate mark-form-data))
                  state/map-home)
         :zoom-level final-zoom
         :marks marks
         :show-crosshairs true
         :auto-pan? false
         :set-coordinate-form-data (fn [coord-str]
                                     (on-update
                                      [:update-field
                                       [:coordinate coord-str]]))})
     (d/label "Coordinate:")
     (d/div
      {:class "coordinate-input-group"}
      (d/span
       {:class "coordinate-text"
        :style {:flex-grow 1}}
       (:coordinate mark-form-data))
      (when original-coordinate
        (d/button
         {:type "button"
          :class "reset-location-btn"
          :onClick #(on-update
                     [:update-field [:coordinate original-coordinate]])
          :title "Reset to original location"}
         icon-reset))
      (d/button
       {:type "button"
        :class "location-btn"
        :onClick #(get-location
                   (fn [[lat lng]]
                     (on-update
                      [:update-field [:coordinate (str lat ", " lng)]])))}
       icon-location))
     (when error
       (d/p
        {:class "error-message"}
        error)))))

(defnc tag-input [{:keys [mark-form-data on-update]}]
  (let [tag-input-ref (hooks/use-ref nil)
        current-tag (:current-tag mark-form-data "")]
    (hooks/use-effect
     :once
     (when-let [el @tag-input-ref]
       (.focus el)))
    (d/div
     {:class "tag-section-wrapper"}
     (d/div
      {:class "form-group"}
      (d/label "Tags:")
      (d/div
       {:class "tag-input-group"}
       (d/input
        {:type "text"
         :ref tag-input-ref
         :value current-tag
         :placeholder "Add a tag and press Enter"
         :onChange #(on-update [:update-current-tag (.. % -target -value)])
         :onKeyDown (fn [e]
                      (when (= (.-key e) "Enter")
                        (.preventDefault e)
                        (on-update [:add-tag])))
         :enterKeyHint "enter"})
       (d/button
        {:type "button"
         :class "add-tag-btn"
         :onClick (fn []
                    (on-update [:add-tag])
                    (when-let [el @tag-input-ref] (.focus el)))}
        "Add"))
      (d/div
       {:class "tags-tags"}
       (map (fn [tag]
              (d/span
               {:key tag
                :class "tag-tag"}
               tag
               (d/button
                {:type "button"
                 :class "remove-tag"
                 :onClick #(on-update [:remove-tag tag])}
                icon-remove)))
            (filter string? (:tags mark-form-data))))))))
