(ns com.hjsoft.mapmarks.website.core
  (:require ["react-dom/client" :as rdom]
            [helix.core :refer [defnc $ <>]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.mapmarks.website.utils :as utils]
            [com.hjsoft.mapmarks.website.state :as state]
            [com.hjsoft.mapmarks.website.controller :as controller]
            [com.hjsoft.mapmarks.website.ui.hooks :refer [use-user-location]]
            [com.hjsoft.mapmarks.website.ui.map :as ui-map :refer [leaflet-map]]
            [com.hjsoft.mapmarks.website.ui.marks
             :refer [marks-list tag-list]]
            [com.hjsoft.mapmarks.website.ui.forms
             :refer [mark-form settings-dialog export-dialog about-dialog]]
            [com.hjsoft.mapmarks.website.ui.layout
             :refer [header fixed-header sticky-wrapper
                     notification-toast loading-indicator]]
            [goog.object :as gobj]
            [com.hjsoft.mapmarks.common.logic :as logic]
            [taoensso.telemere :as tel]))

(tel/set-min-level! :debug)

(def initial-zoom-level 11)
(def fetch-marks-threshold-km (* logic/search-radius-km
                                 logic/fetch-threshold-ratio))

(defn handle-initial-url-params!
  "Parses URL parameters on startup to set initial map center
   and show the add form."
  [dispatch get-location set-show-form]
  (let [params (js/URLSearchParams. (.. js/window -location -search))
        action (.get params "action")
        lat (js/parseFloat (.get params "lat"))
        lon (js/parseFloat (.get params "lon"))
        has-coords? (and (not (js/isNaN lat)) (not (js/isNaN lon)))]
    (if has-coords?
      (dispatch [:set-map-center [lat lon]])
      (get-location (fn [loc] (dispatch [:set-map-center loc]))))
    (when (= action "add")
      (set-show-form true))))

(defn sync-form-state-to-url!
  "Synchronizes the browser URL and page title when showing the Add form."
  [show-form]
  (let [params (js/URLSearchParams. (.. js/window -location -search))
        current-action (.get params "action")]
    (if show-form
      (do
        (set! (.-title js/document) "Add Mark - MapMarks Marks")
        (when (not= current-action "add")
          (.set params "action" "add")
          (js/window.history.pushState #js {} "" (str "?" (.toString params)))))
      (do
        (set! (.-title js/document) "MapMarks Marks")
        (when (= current-action "add")
          (.delete params "action")
          (let [query (.toString params)
                new-url (if (seq query)
                          (str "?" query)
                          (.. js/window -location -pathname))]
            (js/window.history.replaceState #js {} "" new-url)))))))

(defn use-app-side-effects
  [app-state dispatch user-location show-form set-show-form editing-mark]
  (let [{:keys [marks settings map-center map-zoom last-sync]} app-state
        {:keys [get-location]} user-location
        [last-fetched-center set-last-fetched-center] (hooks/use-state map-center)
        app-state-ref (hooks/use-ref app-state)]

    ;; Keep app-state-ref up to date
    (hooks/use-effect
     [app-state]
     (set! (.-current app-state-ref) app-state))

    ;; Local persistence
    (hooks/use-effect
     [marks settings map-center map-zoom last-sync]
     (controller/save-local-data! marks settings map-center map-zoom last-sync))

    ;; Fetch from Remote API on settings change
    (hooks/use-effect
     [settings]
     (when-not (or show-form editing-mark)
       (controller/fetch-remote-marks! (.-current app-state-ref) dispatch)
       (set-last-fetched-center map-center)))

    ;; Automatic upload on login info change
    (let [prev-settings-ref (hooks/use-ref settings)]
      (hooks/use-effect
       [settings]
       (let [prev-settings (.-current prev-settings-ref)
             login-info-keys [:user :password :local-only?]
             login-info-changed? (not= (select-keys settings login-info-keys)
                                       (select-keys prev-settings login-info-keys))
             can-upload? (and (seq (:user settings))
                              (seq (:password settings))
                              (not (:local-only? settings)))]
         (when (and login-info-changed? can-upload? (seq marks))
           (controller/upload-all-marks! (.-current app-state-ref) dispatch))
         (set! (.-current prev-settings-ref) settings))))

    ;; Fetch from Remote API on map-center change beyond threshold
    (hooks/use-effect
     [map-center]
     (let [distance (if (and last-fetched-center map-center)
                      (let [[lat1 lon1] last-fetched-center
                            [lat2 lon2] map-center]
                        (utils/haversine-distance lat1 lon1 lat2 lon2))
                      js/Number.MAX_VALUE)]
       (when (and (not (or show-form editing-mark))
                  (> distance fetch-marks-threshold-km))
         (controller/fetch-remote-marks! (.-current app-state-ref) dispatch)
         (set-last-fetched-center map-center))))

    ;; Initial location fetch, center map, and handle URL parameters
    (hooks/use-effect
     :once
     (handle-initial-url-params! dispatch get-location set-show-form))))

(defnc app [{:keys [geolocation]}]
  (let [[app-state dispatch] (hooks/use-reducer
                              state/app-reducer
                              state/initial-app-state)
        {:keys [marks]} app-state

        [show-form set-show-form] (hooks/use-state false)
        [editing-mark set-editing-mark] (hooks/use-state nil)
        [show-settings-dialog set-show-settings-dialog] (hooks/use-state false)
        [show-export-dialog set-show-export-dialog] (hooks/use-state false)
        [show-about-dialog set-show-about-dialog] (hooks/use-state false)

        user-location (use-user-location
                       dispatch
                       (or geolocation
                           (when (exists? js/navigator) js/navigator.geolocation)))

        _ (use-app-side-effects app-state dispatch user-location show-form set-show-form editing-mark)

        ;; Synchronize show-form state with URL and Page Title
        _ (hooks/use-effect
           [show-form]
           (sync-form-state-to-url! show-form))

        ;; Handle browser back/forward buttons
        _ (hooks/use-effect
           :once
           (let [handler (fn [_]
                           (let [params (js/URLSearchParams. (.. js/window -location -search))
                                 action (.get params "action")]
                             (set-show-form (= action "add"))))]
             (js/window.addEventListener "popstate" handler)
             #(js/window.removeEventListener "popstate" handler)))

        marks-by-expiry (hooks/use-memo
                          [marks (:show-expired? app-state) (:location user-location)]
                          (state/select-marks-by-expiry app-state (:location user-location)))

        filtered-marks (hooks/use-memo
                         [marks-by-expiry (:tag-filter app-state)]
                         (if-let [pf (:tag-filter app-state)]
                           (filterv #(some #{pf} (:tags %)) marks-by-expiry)
                           marks-by-expiry))

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
                      :editing-mark editing-mark
                      :set-editing-mark set-editing-mark
                      :show-settings-dialog show-settings-dialog
                      :set-show-settings-dialog set-show-settings-dialog
                      :show-export-dialog show-export-dialog
                      :set-show-export-dialog set-show-export-dialog
                      :show-about-dialog show-about-dialog
                      :set-show-about-dialog set-show-about-dialog}}}
        (<>
         ($ notification-toast)
         ($ header)
         ($ sticky-wrapper
            ($ leaflet-map
               {:div-id "map-container"
                :marks filtered-marks
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
             {:class "add-mark-btn"
              :onClick #(do
                          (set-editing-mark nil)
                          (set-show-form true))}
             "Add Mark"))
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
          ($ tag-list {:marks marks-by-expiry})
          (when show-form ($ mark-form))
          ($ marks-list {:marks filtered-marks})
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
             :onClick #(controller/upload-all-marks! app-state dispatch)
             :title "Upload all local marks to server"}
            "\u21E7"))
          (when show-settings-dialog ($ settings-dialog {:key "settings"}))
          (when show-export-dialog ($ export-dialog {:key "export"}))
          (when show-about-dialog ($ about-dialog {:key "about"}))))))))

(defn init []
  (let [root (.createRoot rdom (js/document.getElementById "app"))]
    (.render root ($ app))))
