(ns com.hjsoft.roadside.website.ui.forms.inputs
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.ui.map :refer [leaflet-map]]))

(goog-define NODE_TEST false)

(def add-zoom-level 14)

(def icon-reset "\u21BA")
(def icon-location "\u2316")
(def icon-remove "\u2715")

(defnc location-input
  [{:keys
    [stand-form-data
     on-update
     original-coordinate]}]
  (let [app-state (state/use-app-state)
        stands (state/select-stands-by-expiry app-state)
        map-zoom (:map-zoom app-state 11)
        final-zoom (max add-zoom-level map-zoom)
        {:keys [get-location error]} (state/use-user-location-state)]

    (d/div
     {:class "form-group"}
     ($ leaflet-map
        {:div-id "map-form"
         :center (or
                  (utils/parse-coordinates (:coordinate stand-form-data))
                  state/map-home)
         :zoom-level final-zoom
         :stands stands
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
       (:coordinate stand-form-data))
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

(defnc product-input [{:keys [stand-form-data on-update]}]
  (let [product-input-ref (hooks/use-ref nil)
        current-product (:current-product stand-form-data "")]
    (hooks/use-effect
     :once
     (when-let [el @product-input-ref]
       (.focus el)))
    (d/div
     {:class "product-section-wrapper"}
     (d/div
      {:class "form-group"}
      (d/label "Products:")
      (d/div
       {:class "product-input-group"}
       (d/input
        {:type "text"
         :ref product-input-ref
         :value current-product
         :placeholder "Add a product and press Enter"
         :onChange #(on-update [:update-current-product (.. % -target -value)])
         :onKeyDown (fn [e]
                      (when (= (.-key e) "Enter")
                        (.preventDefault e)
                        (on-update [:add-product])))
         :enterKeyHint "enter"})
       (d/button
        {:type "button"
         :class "add-product-btn"
         :onClick (fn []
                    (on-update [:add-product])
                    (when-let [el @product-input-ref] (.focus el)))}
        "Add"))
      (d/div
       {:class "products-tags"}
       (map (fn [product]
              (d/span
               {:key product
                :class "product-tag"}
               product
               (d/button
                {:type "button"
                 :class "remove-tag"
                 :onClick #(on-update [:remove-product product])}
                icon-remove)))
            (filter string? (:products stand-form-data))))))))
