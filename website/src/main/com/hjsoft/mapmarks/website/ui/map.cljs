(ns com.hjsoft.mapmarks.website.ui.map
  (:require [helix.core :refer [defnc]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.mapmarks.website.state :as state]
            [com.hjsoft.mapmarks.website.domain.mark :as mark-domain]
            [com.hjsoft.mapmarks.website.utils :as utils]
            [com.hjsoft.mapmarks.common.logic :as logic]
            [goog.object :as gobj]))

(defonce ^:private leaflet-ref (atom nil))

(defn set-leaflet! [l]
  (reset! leaflet-ref l))

(def L (delay (or @leaflet-ref (throw (js/Error. "Leaflet not initialized. Call set-leaflet! first.")))))

(def ^:const crosshairs-zoom-level 14)

(defn- make-marker
  [{:keys [coord mark set-selected-mark auto-pan?]
    :or {auto-pan? true}}]
  (let [l @L
        marker-fn (gobj/get l "marker")
        marker ^js (marker-fn (clj->js coord))
        popup-content (utils/mark-popup-html mark)
        point-fn (gobj/get l "point")]
    (.bindPopup
     ^js marker
     popup-content
     (clj->js {"autoPan" auto-pan?
               "autoPanPadding" (point-fn 100 100)}))
    (.on ^js marker "click" #(set-selected-mark mark))
    [mark marker]))

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
    (.addTo ^js tl m)
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
  [mark-map center reported-center-ref]
  (hooks/use-effect
   [(first center) (second center) mark-map]
   (when (and mark-map center)
     (let [current-center (.getCenter ^js mark-map)]
       (when (and (coordinates-differ? current-center center)
                  (coordinates-differ? @reported-center-ref center))
         (.setView
          ^js mark-map
          (clj->js center)
          (.getZoom ^js mark-map)
          (clj->js {:animate false})))))))

(defn- same-as-selected? [selected-mark [mark _]]
  (= (mark-domain/mark-key selected-mark)
     (mark-domain/mark-key mark)))

(defn- prepare-marker [auto-pan? dispatch {:keys [coord mark]}]
  (make-marker
   {:coord coord
    :mark mark
    :auto-pan? auto-pan?
    :set-selected-mark #(dispatch [:set-selected-mark %])}))

(defn- use-map-markers
  [mark-map marks selected-mark auto-pan? dispatch]
  (let [layer-group-ref (hooks/use-ref nil)
        prev-selected-ref (hooks/use-ref nil)]
    (hooks/use-effect
     [marks selected-mark mark-map auto-pan?]
     (when mark-map
       (let [selection-changed? (not (identical? selected-mark @prev-selected-ref))
             should-auto-pan? (and auto-pan? selection-changed?)
             locations (->>
                        marks
                        (map (fn [s]
                               {:coord (when (and (:lat s) (:lon s))
                                         [(:lat s) (:lon s)])
                                :mark s}))
                        (remove (comp nil? :coord))
                        (map (partial prepare-marker should-auto-pan? dispatch)))
             new-layer-group (when (seq locations)
                               (let [l ^js @L
                                     lg-fn (gobj/get l "layerGroup")]
                                 (lg-fn (clj->js (map second locations)))))]
         (reset! prev-selected-ref selected-mark)
         (when @layer-group-ref
           (.removeLayer ^js mark-map @layer-group-ref))
         (when new-layer-group
           (.addTo ^js new-layer-group mark-map)
           (reset! layer-group-ref new-layer-group)
           (some->>
            locations
            (filter (partial same-as-selected? selected-mark))
            first
            second
            (#(.openPopup ^js %)))))))))

(defn- use-user-location-marker
  [mark-map location]
  (let [current-location-marker-ref (hooks/use-ref nil)]
    (hooks/use-effect
     [location mark-map]
     (when mark-map
       (when @current-location-marker-ref
         (.removeLayer ^js mark-map @current-location-marker-ref))
       (when location
         (let [marker (make-current-location-marker location)]
           (.addTo ^js marker mark-map)
           (reset! current-location-marker-ref marker)))))))

(def ^:const search-radius-m (* logic/search-radius-km 1000))

(defn- get-map-width-m [mark-map]
  (let [center (.getCenter ^js mark-map)
        bounds (.getBounds ^js mark-map)
        east (.getEast ^js bounds)
        ;; Distance from center to the right edge (semi-width)
        half-width-m (.distanceTo ^js center (clj->js {:lat (.-lat ^js center) :lng east}))]
    (* 2 half-width-m)))

(defn- view-wider-than-diameter? [mark-map radius-m]
  (> (get-map-width-m mark-map) (* radius-m 2)))

(defn- use-search-radius-marker
  [mark-map loading?]
  (let [marker-ref (hooks/use-ref nil)
        linger-timeout-ref (hooks/use-ref nil)
        fade-timeout-ref (hooks/use-ref nil)]
    (hooks/use-effect
     [mark-map loading?]
     (when mark-map
       (if loading?
         (do
           (when @linger-timeout-ref (js/clearTimeout @linger-timeout-ref))
           (when @fade-timeout-ref (js/clearTimeout @fade-timeout-ref))
           (when @marker-ref (.removeLayer ^js mark-map @marker-ref))
           (if (view-wider-than-diameter? mark-map search-radius-m)
             (let [l ^js @L
                   circle-fn (gobj/get l "circle")
                   marker (circle-fn
                           (.getCenter ^js mark-map)
                           (clj->js {:radius search-radius-m
                                     :color "#666"
                                     :weight 1
                                     :fill true
                                     :fillColor "#666"
                                     :fillOpacity 0.1
                                     :dashArray "5, 10"
                                     :interactive false
                                     :className "search-radius-circle"}))]
               (.addTo ^js marker mark-map)
               (reset! marker-ref marker))
             (reset! marker-ref nil)))
         (when @marker-ref
           (let [m @marker-ref]
             (reset! linger-timeout-ref
                     (js/setTimeout
                      (fn []
                        (when-let [path (.-_path ^js m)]
                          (gobj/set (.-style path) "opacity" "0")
                          (gobj/set (.-style path) "fill-opacity" "0"))
                        (reset! fade-timeout-ref
                                (js/setTimeout
                                 (fn []
                                   (when (and mark-map m)
                                     (.removeLayer ^js mark-map m))
                                   (reset! marker-ref nil))
                                 500)))
                      1000))))))
     (fn []
       (when @linger-timeout-ref (js/clearTimeout @linger-timeout-ref))
       (when @fade-timeout-ref (js/clearTimeout @fade-timeout-ref))))

    (hooks/use-effect
     [mark-map]
     (fn []
       (when (and mark-map @marker-ref)
         (.removeLayer ^js mark-map @marker-ref)
         (reset! marker-ref nil))))))

(defn use-leaflet-map
  [{:keys [div-id center marks selected-mark zoom-level
           set-coordinate-form-data auto-pan?]
    :or {auto-pan? true}}]
  (let [app-state (state/use-app-state)
        dispatch (state/use-dispatch)
        {:keys [location]} (state/use-user-location-state)
        marks (or marks (:marks app-state))
        selected-mark (or selected-mark (:selected-mark app-state))
        center (or center (:map-center app-state))
        center-ref (hooks/use-ref center)
        reported-center-ref (hooks/use-ref nil)
        [mark-map set-mark-map] (hooks/use-state nil)
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
       (set-mark-map m)
       ;; Ensure map is correctly sized after modal animation/render
       (js/setTimeout
        (fn []
          (.invalidateSize m)
          (.setView m (clj->js @center-ref) zoom-level))
        100)))

    (use-map-center mark-map center reported-center-ref)
    (use-map-markers mark-map marks selected-mark auto-pan? dispatch)
    (use-user-location-marker mark-map location)
    (use-search-radius-marker mark-map (:loading-marks? app-state))
    {:mark-map mark-map
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
