(ns com.hjsoft.roadside.website.core
  (:require ["react-dom/client" :as rdom]
            [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [taoensso.telemere :as tel]
            [com.hjsoft.roadside.website.api :as api]
            [com.hjsoft.roadside.website.storage :as storage]
            [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.sync :as sync]
            [com.hjsoft.roadside.website.ui.map :refer [leaflet-map]]
            [com.hjsoft.roadside.website.ui.stands :refer [stands-list product-list]]
            [com.hjsoft.roadside.website.ui.forms :refer [stand-form settings-dialog]]
            [com.hjsoft.roadside.website.ui.layout :refer [header fixed-header notification-toast]]
            [cljs.core.async :refer [go <!]]))

(def add-zoom-level 16)
(def initial-zoom-level 11)

(defn use-user-location []
  (let [[location set-location] (hooks/use-state nil)
        [error set-error] (hooks/use-state nil)
        [is-locating set-is-locating] (hooks/use-state false)
        cancelled-ref (hooks/use-ref false)
        get-location (hooks/use-callback
                      :once
                      (fn [& [on-success on-error]]
                        (reset! cancelled-ref false)
                        (set-is-locating true)
                        (set-error nil)
                        (if (and
                             (exists? js/navigator)
                             (exists? js/navigator.geolocation))
                          (js/navigator.geolocation.getCurrentPosition
                           (fn [position]
                             (when-not @cancelled-ref
                               (let [coords (.-coords position)
                                     loc [(.-latitude coords)
                                          (.-longitude coords)]]
                                 (set-location loc)
                                 (set-is-locating false)
                                 (when on-success (on-success loc)))))
                           (fn [err]
                             (when-not @cancelled-ref
                               (let [msg (.-message err)]
                                 (tel/log! :error {:failed-location msg})
                                 (set-error "Unable to retrieve location.")
                                 (set-is-locating false)
                                 (when on-error (on-error msg))))))
                          (do
                            (set-error "Geolocation not supported.")
                            (set-is-locating false)
                            (when on-error (on-error "Geolocation not supported."))))))
        cancel-location (hooks/use-callback
                         :once
                         (fn []
                           (reset! cancelled-ref true)
                           (set-is-locating false)))]
    (hooks/use-memo
     [location error is-locating get-location cancel-location]
     {:location location
      :error error
      :is-locating is-locating
      :get-location get-location
      :cancel-location cancel-location})))

(defnc app []
  (let [[app-state dispatch] (hooks/use-reducer state/app-reducer state/initial-app-state)
        {:keys [stands show-form editing-stand stand-form-data product-filter
                selected-stand map-center show-settings-dialog
                settings-form-data settings is-synced notification]} app-state

        user-location (use-user-location)
        {:keys [location error is-locating get-location cancel-location]} user-location

        show-notification (fn [type message]
                            (dispatch [:set-notification {:type type :message message}]))

        filtered-stands (hooks/use-memo
                         [stands product-filter]
                         (let [sorted-stands (sort-by :updated #(compare %2 %1) stands)]
                           (if product-filter
                             (vec (filter
                                   #(some
                                     (fn [p] (= p product-filter))
                                     (:products %))
                                   sorted-stands))
                             sorted-stands)))

        set-coordinate-form-data (hooks/use-callback
                                  [dispatch]
                                  (fn [c] (dispatch [:set-map-center (utils/parse-coordinates c)])))]

    ;; Persist to Local Storage
    (hooks/use-effect
     [stands settings map-center]
     (sync/save-local-data! stands settings map-center))

    ;; Update map-center when location changes
    (hooks/use-effect
     [location]
     (when location
       (dispatch [:set-map-center location])))

    ;; Sync with Remote API
    (hooks/use-effect
     [stands is-synced settings]
     (sync/save-remote-stands! app-state show-notification))

    ;; Fetch from Remote API on settings change
    (hooks/use-effect
     [settings]
     (sync/fetch-remote-stands! app-state dispatch show-notification))

    ;; Initialize
    (hooks/use-effect
     :once
     (get-location))

    (d/div
     {:class "app-container"}

     ($ notification-toast {:notification notification
                            :set-notification #(dispatch [:set-notification %])})
     ($ fixed-header
        ($ header)
        ($ leaflet-map
           {:div-id "map-container"
            :center map-center
            :stands filtered-stands
            :zoom-level initial-zoom-level
            :set-selected-stand #(dispatch [:set-selected-stand %])
            :selected-stand selected-stand
            :is-locating is-locating
            :on-cancel-location cancel-location
            :set-coordinate-form-data set-coordinate-form-data
            :current-location-coords location}))
     (d/div
      {:class "content"}
      (d/div
       {:class "main-actions"}
       (d/button
        {:class "add-stand-btn"
         :onClick #(dispatch [:set-show-form true])}
        "Add Stand")
       (d/div
        {:class "map-actions-right"}
        (when error
          (d/p {:class "error-message"} error))
        (d/button
         {:type "button"
          :class "location-btn"
          :onClick get-location}
         "\u2316")))
      ($ product-list
         {:stands stands
          :set-product-filter #(dispatch [:set-product-filter %])
          :product-filter product-filter})
      ($ stand-form
         {:form-data stand-form-data
          :set-form-data #(dispatch [:set-stand-form-data %])
          :show-form show-form
          :set-show-form #(dispatch [:set-show-form %])
          :user-location user-location
          :user-map-center map-center
          :editing-stand editing-stand
          :set-editing-stand #(dispatch [:set-editing-stand %])
          :stands stands
          :set-stands #(dispatch [:set-stands %])
          :map-home state/map-home
          :add-zoom-level add-zoom-level})
      ($ stands-list
         {:stands filtered-stands
          :set-stands #(dispatch [:set-stands %])
          :set-editing-stand #(dispatch [:set-editing-stand %])
          :set-form-data #(dispatch [:set-stand-form-data %])
          :set-show-form #(dispatch [:set-show-form %])
          :selected-stand selected-stand
          :set-selected-stand #(dispatch [:set-selected-stand %])})
      (d/button
       {:class "settings-btn"
        :onClick #(dispatch [:set-show-settings-dialog true])}
       "\u2699")
      ($ settings-dialog
         {:show-settings-dialog show-settings-dialog
          :set-show-settings-dialog #(dispatch [:set-show-settings-dialog %])
          :form-data settings-form-data
          :set-form-data #(dispatch [:set-settings-form-data %])
          :set-settings #(dispatch [:set-settings %])})))))

(defn init []
  (let [root (.createRoot rdom (js/document.getElementById "app"))]
    (.render root ($ app))))