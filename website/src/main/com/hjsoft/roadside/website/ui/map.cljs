(ns com.hjsoft.roadside.website.ui.map
  (:require [helix.core :refer [defnc]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.domain.stand :as stand-domain]
            [com.hjsoft.roadside.website.utils :as utils]
            [goog.object :as gobj]))

(defonce ^:private leaflet-ref (atom nil))

(defn set-leaflet! [l]
  (reset! leaflet-ref l))

(def L (delay (or @leaflet-ref (throw (js/Error. "Leaflet not initialized. Call set-leaflet! first.")))))

(def ^:const crosshairs-zoom-level 12)

(defn- make-marker
  [{:keys [coord stand set-selected-stand auto-pan?]
    :or {auto-pan? true}}]
  (let [l @L
        marker-fn (gobj/get l "marker")
        marker ^js (marker-fn (clj->js coord))
        popup-content (utils/stand-popup-html stand)
        point-fn (gobj/get l "point")]
    (.bindPopup
     ^js marker
     popup-content
     (clj->js {"autoPan" auto-pan?
               "autoPanPadding" (point-fn 100 100)}))
    (.on ^js marker "click" #(set-selected-stand stand))
    [stand marker]))

(defn- make-current-location-marker
  [coord]
  (let [l @L
        cm-fn (gobj/get l "circleMarker")]
    (cm-fn (clj->js coord)
           (clj->js {:radius 6
                     :color "#ffffff"
                     :fillColor "#3388ff"
                     :fillOpacity 0.8
                     :weight 1}))))

(defn- init-map [div-id center zoom-level]
  (let [l @L
        map-fn (gobj/get l "map")
        m ^js (map-fn div-id)
        tl-fn (gobj/get l "tileLayer")
        tl ^js (tl-fn "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                      (clj->js {:attribution "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors"}))]
    (.setView m (clj->js center) zoom-level)
    (.addTo tl m)
    m))

(defn coordinates-differ?
  [c1 c2]
  (if (and c1 c2)
    (let [lat1 (if (vector? c1) (first c1) (.-lat ^js c1))
          lng1 (if (vector? c1) (second c1) (.-lng ^js c1))
          lat2 (if (vector? c2) (first c2) (.-lat ^js c2))
          lng2 (if (vector? c2) (second c2) (.-lng ^js c2))]
      (or (not= (.toFixed lat1 6) (.toFixed lat2 6))
          (not= (.toFixed lng1 6) (.toFixed lng2 6))))
    (not (and (nil? c1) (nil? c2)))))

(defn- use-map-center
  [stand-map center reported-center-ref]
  (hooks/use-effect
   [(first center) (second center) stand-map]
   (when (and stand-map center)
     (let [current-center (.getCenter ^js stand-map)]
       (when (and (coordinates-differ? current-center center)
                  (coordinates-differ? @reported-center-ref center))
         (.setView
          ^js stand-map
          (clj->js center)
          (.getZoom ^js stand-map)
          (clj->js {:animate false})))))))

(defn- same-as-selected? [selected-stand [stand _]]
  (= (stand-domain/stand-key selected-stand)
     (stand-domain/stand-key stand)))

(defn- prepare-marker [auto-pan? dispatch {:keys [coord stand]}]
  (make-marker
   {:coord coord
    :stand stand
    :auto-pan? auto-pan?
    :set-selected-stand #(dispatch [:set-selected-stand %])}))

(defn- use-map-markers
  [stand-map stands selected-stand auto-pan? dispatch]
  (let [layer-group-ref (hooks/use-ref nil)
        prev-selected-ref (hooks/use-ref nil)]
    (hooks/use-effect
     [stands selected-stand stand-map auto-pan?]
     (when stand-map
       (let [selection-changed? (not (identical? selected-stand @prev-selected-ref))
             should-auto-pan? (and auto-pan? selection-changed?)
             locations (->>
                        stands
                        (map (fn [s]
                               {:coord (when (and (:lat s) (:lon s))
                                         [(:lat s) (:lon s)])
                                :stand s}))
                        (remove (comp nil? :coord))
                        (map (partial prepare-marker should-auto-pan? dispatch)))
             new-layer-group (when (seq locations)
                               (let [l ^js @L
                                     lg-fn (gobj/get l "layerGroup")]
                                 (lg-fn (clj->js (map second locations)))))]
         (reset! prev-selected-ref selected-stand)
         (when @layer-group-ref
           (.removeLayer ^js stand-map @layer-group-ref))
         (when new-layer-group
           (.addTo ^js new-layer-group stand-map)
           (reset! layer-group-ref new-layer-group)
           (some->>
            locations
            (filter (partial same-as-selected? selected-stand))
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
        center-ref (hooks/use-ref center)
        reported-center-ref (hooks/use-ref nil)
        [stand-map set-stand-map] (hooks/use-state nil)
        [current-zoom set-current-zoom] (hooks/use-state zoom-level)]

    (hooks/use-effect
     [center]
     (reset! center-ref center))

    ;; Initialization
    (hooks/use-effect
     :once
     (let [m ^js (init-map div-id center zoom-level)]
       (when set-coordinate-form-data
         (dispatch [:set-map-zoom zoom-level]))
       (.on
        m
        "moveend zoomend"
        (fn []
          (let [center-val ^js (.getCenter m)
                zoom (.getZoom m)
                lat (.-lat center-val)
                lng (.-lng center-val)]
            (set-current-zoom zoom)
            (when set-coordinate-form-data
              (reset! reported-center-ref [lat lng])
              (dispatch [:set-map-zoom zoom])
              (set-coordinate-form-data
               (str lat ", " lng))))))
       (set-stand-map m)
       ;; Ensure map is correctly sized after modal animation/render
       (js/setTimeout
        (fn []
          (.invalidateSize m)
          (.setView m (clj->js @center-ref) zoom-level))
        100)))

    (use-map-center stand-map center reported-center-ref)
    (use-map-markers stand-map stands selected-stand auto-pan? dispatch)
    (use-user-location-marker stand-map location)
    {:stand-map stand-map
     :zoom current-zoom}))

(defnc leaflet-map
  [{:keys [div-id show-crosshairs] :as props}]
  (let [{:keys [is-locating cancel-location]} (state/use-user-location-state)
        {:keys [zoom]} (use-leaflet-map props)
        show-crosshairs (or show-crosshairs (>= zoom crosshairs-zoom-level))]
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
