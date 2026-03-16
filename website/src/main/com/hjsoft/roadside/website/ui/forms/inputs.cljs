(ns com.hjsoft.roadside.website.ui.forms.inputs
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.ui.map :refer [leaflet-map]]))

(goog-define NODE_TEST false)

(def add-zoom-level 14)

(defnc location-input
  [{:keys
    [stand-form-data
     on-update
     original-coordinate]}]
  (let [app-state (state/use-app-state)
        stands (state/select-stands-by-expiry app-state)
        map-zoom (:map-zoom app-state 11)
        final-zoom (max add-zoom-level map-zoom)
        {:keys [get-location error]} (state/use-user-location-state)
        [coordinate-display
         set-coordinate-display] (hooks/use-state
                                  (:coordinate stand-form-data))
        coordinate-input-ref (hooks/use-ref nil)]

    (hooks/use-effect
     :once
     (when-let [el @coordinate-input-ref]
       (.focus el)))

    (hooks/use-effect
     [(:coordinate stand-form-data)]
     (set-coordinate-display (:coordinate stand-form-data)))

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
      (d/input
       {:type "text"
        :ref coordinate-input-ref
        :value coordinate-display
        :onChange #(set-coordinate-display (.. % -target -value))
        :onBlur #(on-update [:update-field [:coordinate coordinate-display]])
        :class "coordinate-input"})
      (when original-coordinate
        (d/button
         {:type "button"
          :class "reset-location-btn"
          :onClick #(on-update
                     [:update-field [:coordinate original-coordinate]])
          :title "Reset to original location"}
         "\u21BA"))
      (d/button
       {:type "button"
        :class "location-btn"
        :onClick #(get-location
                   (fn [[lat lng]]
                     (on-update
                      [:update-field [:coordinate (str lat ", " lng)]])))}
       "\u2316"))
     (when error
       (d/p
        {:class "error-message"}
        error)))))

(defnc product-input [{:keys [stand-form-data on-update]}]
  (let [product-input-ref (hooks/use-ref nil)
        current-product (:current-product stand-form-data "")]
    (d/div
     {:class "product-section-wrapper"}
     (d/div
      {:class "form-group"}
      (d/label "Products:")
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
                "\u2715")))
            (filter string? (:products stand-form-data))))
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
        "Add"))))))
