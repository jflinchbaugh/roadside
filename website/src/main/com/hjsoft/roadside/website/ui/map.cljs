(ns com.hjsoft.roadside.website.ui.map
  (:require [helix.core :refer [defnc]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            ["leaflet" :as L]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.utils :as utils]))

(defn- make-marker
  [{:keys [coord stand set-selected-stand auto-pan?]
    :or {auto-pan? true}}]
  (let [marker (L/marker (clj->js coord))
        popup-content (utils/stand-popup-html stand)]
    (.bindPopup
     marker
     popup-content
     (clj->js {"autoPan" auto-pan?
               "autoPanPadding" (L/point 100 100)}))
    (.on marker "click" #(set-selected-stand stand))
    [stand marker]))

(defn- make-current-location-marker
  [coord]
  (L/circleMarker (clj->js coord)
                  (clj->js {:radius 6
                            :color "#ffffff"
                            :fillColor "#3388ff"
                            :fillOpacity 0.8
                            :weight 1})))

(defn- init-map [div-id center zoom-level]
  (let [m (L/map div-id)
        tl (L/tileLayer
            "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png")]
    (.setView m (clj->js center) zoom-level)
    (.addTo tl m)
    m))

(defn- use-map-center
  [stand-map center]
  (hooks/use-effect
   [(first center) (second center) stand-map]
   (when (and stand-map center)
     (let [current-center (.getCenter ^js stand-map)
           new-lat (first center)
           new-lng (second center)]
       (when (or (not= (.toFixed (.-lat current-center) 6) (.toFixed new-lat 6))
                 (not= (.toFixed (.-lng current-center) 6) (.toFixed new-lng 6)))
         (.setView
          ^js stand-map
          (clj->js center)
          (.getZoom ^js stand-map)
          (clj->js {:animate false})))))))

(defn- use-map-markers
  [stand-map stands selected-stand auto-pan? dispatch]
  (let [layer-group-ref (hooks/use-ref nil)]
    (hooks/use-effect
     [stands selected-stand stand-map auto-pan?]
     (when stand-map
       (let [locations (->>
                        stands
                        (map (fn [s]
                               {:coord (utils/parse-coordinates (:coordinate s))
                                :stand s}))
                        (remove (comp nil? :coord))
                        (map
                         (fn [{:keys [coord stand]}]
                           (make-marker
                            {:coord coord
                             :stand stand
                             :auto-pan? auto-pan?
                             :set-selected-stand #(dispatch [:set-selected-stand %])}))))
             new-layer-group (when (seq locations)
                               (L/layerGroup
                                (clj->js (map second locations))))]
         (when @layer-group-ref
           (.removeLayer ^js stand-map @layer-group-ref))
         (when new-layer-group
           (.addTo ^js new-layer-group stand-map)
           (reset! layer-group-ref new-layer-group)
           (some->>
            locations
            (filter
             (fn [[s _]]
               (= (utils/stand-key selected-stand) (utils/stand-key s))))
            first
            second
            (#(.openPopup ^js %)))))))))

(defn- use-user-location-marker
  [stand-map location]
  (let [current-location-marker-ref (hooks/use-ref nil)]
    (hooks/use-effect
     [location stand-map]
     (when stand-map
       (when @current-location-marker-ref
         (.removeLayer ^js stand-map @current-location-marker-ref))
       (when location
         (let [marker (make-current-location-marker location)]
           (.addTo ^js marker stand-map)
           (reset! current-location-marker-ref marker)))))))

(defn use-leaflet-map
  [{:keys [div-id center stands selected-stand zoom-level
           set-coordinate-form-data auto-pan?]
    :or {auto-pan? true}}]
  (let [app-state (state/use-app-state)
        dispatch (state/use-dispatch)
        {:keys [location]} (state/use-user-location-state)
        stands (or stands (:stands app-state))
        selected-stand (or selected-stand (:selected-stand app-state))
        center (or center (:map-center app-state))
        [stand-map set-stand-map] (hooks/use-state nil)]

    ;; Initialization
    (hooks/use-effect
     :once
     (let [m (init-map div-id center zoom-level)]
       (when set-coordinate-form-data
         (.on
          m
          "moveend"
          (fn []
            (let [center (.getCenter m)]
              (set-coordinate-form-data
               (str (.-lat center) ", " (.-lng center)))))))
       (set-stand-map m)
       ;; Ensure map is correctly sized after modal animation/render
       (js/setTimeout
        (fn []
          (.invalidateSize m)
          (.setView m (clj->js center) zoom-level))
        100)))

    (use-map-center stand-map center)
    (use-map-markers stand-map stands selected-stand auto-pan? dispatch)
    (use-user-location-marker stand-map location)))

(defnc leaflet-map
  [{:keys [div-id show-crosshairs] :as props}]
  (let [{:keys [is-locating cancel-location]} (state/use-user-location-state)]
    (use-leaflet-map props)
    (d/div {:id div-id
            :class "map-wrapper"}
           (when show-crosshairs
             (d/div {:class "crosshairs"}))
           (when is-locating
             (d/div
              {:class "loading-overlay"
               :onClick #(cancel-location)}
              (d/div {:class "spinner"})
              (d/p "Locating..."))))))
