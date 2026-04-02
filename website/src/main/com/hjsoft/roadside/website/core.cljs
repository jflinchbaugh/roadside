(ns com.hjsoft.roadside.website.core
  (:require ["react-dom/client" :as rdom]
            [helix.core :refer [defnc $ <>]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.controller :as controller]
            [com.hjsoft.roadside.website.ui.hooks :refer [use-user-location]]
            [com.hjsoft.roadside.website.ui.map :as ui-map :refer [leaflet-map]]
            [com.hjsoft.roadside.website.ui.stands
             :refer [stands-list product-list]]
            [com.hjsoft.roadside.website.ui.forms
             :refer [stand-form settings-dialog export-dialog]]
            [com.hjsoft.roadside.website.ui.layout
             :refer [header fixed-header sticky-wrapper notification-toast loading-indicator]]
            [goog.object :as gobj]
            [taoensso.telemere :as tel]))

(tel/set-min-level! :debug)

(def initial-zoom-level 11)
(def fetch-stands-threshold-km 100.0)

(defn use-app-side-effects
  [app-state dispatch user-location show-form editing-stand]
  (let [{:keys [stands settings map-center map-zoom last-sync]} app-state
        {:keys [get-location]} user-location
        [last-fetched-center set-last-fetched-center] (hooks/use-state map-center)
        app-state-ref (hooks/use-ref app-state)]

    ;; Keep app-state-ref up to date
    (hooks/use-effect
     [app-state]
     (set! (.-current app-state-ref) app-state))

    ;; Local persistence
    (hooks/use-effect
     [stands settings map-center map-zoom last-sync]
     (controller/save-local-data! stands settings map-center map-zoom last-sync))

    ;; Fetch from Remote API on settings change
    (hooks/use-effect
     [settings]
     (when-not (or show-form editing-stand)
       (controller/fetch-remote-stands! (.-current app-state-ref) dispatch)
       (set-last-fetched-center map-center)))

    ;; Fetch from Remote API on map-center change beyond threshold
    (hooks/use-effect
     [map-center]
     (let [distance (if (and last-fetched-center map-center)
                      (let [[lat1 lon1] last-fetched-center
                            [lat2 lon2] map-center]
                        (utils/haversine-distance lat1 lon1 lat2 lon2))
                      js/Number.MAX_VALUE)]
       (when (and (not (or show-form editing-stand))
                  (> distance fetch-stands-threshold-km))
         (controller/fetch-remote-stands! (.-current app-state-ref) dispatch)
         (set-last-fetched-center map-center))))

    ;; Initial location fetch and center map
    (hooks/use-effect
     :once
     (get-location (fn [loc] (dispatch [:set-map-center loc]))))))

(defnc app [{:keys [geolocation]}]
  (let [[app-state dispatch] (hooks/use-reducer
                              state/app-reducer
                              state/initial-app-state)
        {:keys [stands]} app-state

        [show-form set-show-form] (hooks/use-state false)
        [editing-stand set-editing-stand] (hooks/use-state nil)
        [show-settings-dialog set-show-settings-dialog] (hooks/use-state false)
        [show-export-dialog set-show-export-dialog] (hooks/use-state false)

        user-location (use-user-location
                       dispatch
                       (or geolocation
                           (when (exists? js/navigator) js/navigator.geolocation)))

        _ (use-app-side-effects app-state dispatch user-location show-form editing-stand)

        stands-by-expiry (hooks/use-memo
                          [stands (:show-expired? app-state) (:location user-location)]
                          (state/select-stands-by-expiry app-state (:location user-location)))

        filtered-stands (hooks/use-memo
                         [stands-by-expiry (:product-filter app-state)]
                         (if-let [pf (:product-filter app-state)]
                           (filterv #(some #{pf} (:products %)) stands-by-expiry)
                           stands-by-expiry))

        set-coordinate-form-data (hooks/use-memo
                                  [dispatch]
                                  (utils/debounce
                                   (fn [c]
                                     (dispatch
                                      [:set-map-center
                                       (utils/parse-coordinates c)]))
                                   50))]

    (d/div
     {:class "app-container"}
     ($ (gobj/get state/app-context "Provider")
        {:value {:state app-state
                 :dispatch dispatch
                 :user-location user-location
                 :ui {:show-form show-form
                      :set-show-form set-show-form
                      :editing-stand editing-stand
                      :set-editing-stand set-editing-stand
                      :show-settings-dialog show-settings-dialog
                      :set-show-settings-dialog set-show-settings-dialog
                      :show-export-dialog show-export-dialog
                      :set-show-export-dialog set-show-export-dialog}}}
        (<>
         ($ notification-toast)
         ($ header)
         ($ sticky-wrapper
            ($ leaflet-map
               {:div-id "map-container"
                :stands filtered-stands
                :zoom-level initial-zoom-level
                :set-coordinate-form-data set-coordinate-form-data})
            ($ loading-indicator))
         (d/div
          {:class "content"}
          (d/div
           {:class "main-actions"}
           (d/div
            {:class "main-buttons"}
            (d/button
             {:class "add-stand-btn"
              :onClick #(do
                          (set-editing-stand nil)
                          (set-show-form true))}
             "Add Stand"))
           (d/div
            {:class "map-actions-right"}
            (when (and (:error user-location) (string? (:error user-location)))
              (d/p {:class "error-message"} (:error user-location)))
            (d/button
             {:type "button"
              :class "location-btn"
              :onClick #((:get-location user-location)
                         (fn [loc] (dispatch [:set-map-center loc])))}
             "\u2316")))
          ($ product-list {:stands stands-by-expiry})
          (when show-form ($ stand-form))
          ($ stands-list {:stands filtered-stands})
          (d/div
           {:class "bottom-actions"}
           (d/div
            {:class "left-bottom-actions"}
            (d/button
             {:class "settings-btn"
              :onClick #(set-show-settings-dialog true)
              :title "Settings"}
             "\u2699")
            (d/button
             {:class "export-btn"
              :onClick #(set-show-export-dialog true)
              :title "Google Maps Integration"}
             "\u2913")) ;; Downwards arrow to bar
           (d/button
            {:class "upload-all-btn"
             :onClick #(controller/upload-all-stands! app-state dispatch)
             :title "Upload all local stands to server"}
            "\u21E7"))
          (when show-settings-dialog ($ settings-dialog))
          (when show-export-dialog ($ export-dialog))))))))

(defn init []
  (let [root (.createRoot rdom (js/document.getElementById "app"))]
    (.render root ($ app))))
