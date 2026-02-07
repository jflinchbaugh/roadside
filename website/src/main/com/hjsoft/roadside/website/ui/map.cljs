(ns com.hjsoft.roadside.website.ui.map
  (:require [helix.core :refer [defnc]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            ["leaflet" :as L]
            [com.hjsoft.roadside.website.utils :as utils]))

(defn- make-marker
  [{:keys [coord stand set-selected-stand]}]
  (let [marker (L/marker (clj->js coord))
        content (str
                 (when-not (empty? (:name stand))
                   (str "<b>" (:name stand) "</b><br>"))
                 (when (seq (:products stand))
                   (str
                    (clojure.string/join ", " (:products stand))
                    "<br>")))
        popup-content (if (empty? content)
                        "(no details)"
                        content)]
    (.bindPopup
     marker
     popup-content
     (clj->js {"autoPanPadding" (L/point 100 100)}))
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

(defn use-leaflet-map
  [{:keys [div-id center zoom-level stands selected-stand set-selected-stand
           set-coordinate-form-data map-ref current-location-coords is-locating]}]
  (let [[stand-map set-stand-map] (hooks/use-state nil)
        layer-group-ref (hooks/use-ref nil)
        current-location-marker-ref (hooks/use-ref nil)]

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
       (when map-ref
         (reset! map-ref m))
       (set-stand-map m)))

    ;; Sync Center
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
            (clj->js {:animate false}))))))

    ;; Sync Stands & Selection
    (hooks/use-effect
     [stands selected-stand stand-map]
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
                             :set-selected-stand (or
                                                  set-selected-stand
                                                  (constantly nil))}))))
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
            (#(.openPopup ^js %)))))))

    ;; Sync Current Location
    (hooks/use-effect
     [current-location-coords is-locating stand-map]
     (when stand-map
       (when @current-location-marker-ref
         (.removeLayer ^js stand-map @current-location-marker-ref))
       (when current-location-coords
         (let [marker (make-current-location-marker current-location-coords)]
           (.addTo ^js marker stand-map)
           (reset! current-location-marker-ref marker)))))))

(defnc leaflet-map
  [{:keys [div-id show-crosshairs is-locating on-cancel-location] :as props}]
  (use-leaflet-map props)
  (d/div {:id div-id
          :style {:position "relative"}}
         (when show-crosshairs
           (d/div {:class "crosshairs"}))
         (when is-locating
           (d/div
            {:class "loading-overlay"
             :onClick #(when on-cancel-location (on-cancel-location))}
            (d/div {:class "spinner"})
            (d/p "Locating...")))))