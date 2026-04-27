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
             :refer [stand-form settings-dialog export-dialog about-dialog]]
            [com.hjsoft.roadside.website.ui.layout
             :refer [header fixed-header sticky-wrapper
                     notification-toast loading-indicator]]
            [goog.object :as gobj]
            [com.hjsoft.roadside.common.logic :as logic]
            [taoensso.telemere :as tel]))

(tel/set-min-level! :debug)

(def initial-zoom-level 11)
(def fetch-stands-threshold-km (* logic/search-radius-km
                                 logic/fetch-threshold-ratio))

(defn handle-initial-url-params!
  "Parses URL parameters on startup to set initial map center
   and show the add form."
  [dispatch get-location set-show-form]
  (let [params (js/URLSearchParams. (.. js/window -location -search))
        action (.get params "action")
        lat (js/parseFloat (.get params "lat"))
        lon (js/parseFloat (.get params "lon"))]
    (if (and (not (js/isNaN lat)) (not (js/isNaN lon)))
      (dispatch [:set-map-center [lat lon]])
      (get-location (fn [loc] (dispatch [:set-map-center loc]))))
    (when (= action "add")
      ;; Use replaceState if already there to avoid back-button loop
      (js/window.history.replaceState #js {} "" (.. js/window -location -href))
      (set-show-form true))))

(defn sync-form-state-to-url!
  "Synchronizes the browser URL and page title when showing the Add form."
  [show-form]
  (let [params (js/URLSearchParams. (.. js/window -location -search))
        current-action (.get params "action")]
    (if show-form
      (do
        (set! (.-title js/document) "Add Stand - Roadside Stands")
        (when (not= current-action "add")
          (.set params "action" "add")
          (js/window.history.pushState #js {} "" (str "?" (.toString params)))))
      (do
        (set! (.-title js/document) "Roadside Stands")
        (when (= current-action "add")
          (.delete params "action")
          (let [query (.toString params)
                new-url (if (seq query)
                          (str "?" query)
                          (.. js/window -location -pathname))]
            (js/window.history.replaceState #js {} "" new-url)))))))

(defn use-app-side-effects
  [app-state dispatch user-location show-form set-show-form editing-stand]
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
         (when (and login-info-changed? can-upload? (seq stands))
           (controller/upload-all-stands! (.-current app-state-ref) dispatch))
         (set! (.-current prev-settings-ref) settings))))

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

    ;; Initial location fetch, center map, and handle URL parameters
    (hooks/use-effect
     :once
     (handle-initial-url-params! dispatch get-location set-show-form))))

(defnc app [{:keys [geolocation]}]
  (let [[app-state dispatch] (hooks/use-reducer
                              state/app-reducer
                              state/initial-app-state)
        {:keys [stands]} app-state

        [show-form set-show-form] (hooks/use-state false)
        [editing-stand set-editing-stand] (hooks/use-state nil)
        [show-settings-dialog set-show-settings-dialog] (hooks/use-state false)
        [show-export-dialog set-show-export-dialog] (hooks/use-state false)
        [show-about-dialog set-show-about-dialog] (hooks/use-state false)

        user-location (use-user-location
                       dispatch
                       (or geolocation
                           (when (exists? js/navigator) js/navigator.geolocation)))

        _ (use-app-side-effects app-state dispatch user-location show-form set-show-form editing-stand)

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
                      :set-show-export-dialog set-show-export-dialog
                      :show-about-dialog show-about-dialog
                      :set-show-about-dialog set-show-about-dialog}}}
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
          (when show-settings-dialog ($ settings-dialog {:key "settings"}))
          (when show-export-dialog ($ export-dialog {:key "export"}))
          (when show-about-dialog ($ about-dialog {:key "about"}))))))))

(defn init []
  (let [root (.createRoot rdom (js/document.getElementById "app"))]
    (.render root ($ app))))
