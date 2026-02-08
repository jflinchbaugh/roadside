(ns com.hjsoft.roadside.website.core
  (:require ["react-dom/client" :as rdom]
            [helix.core :refer [defnc $ <>]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [taoensso.telemere :as tel]
            [com.hjsoft.roadside.website.api :as api]
            [com.hjsoft.roadside.website.storage :as storage]
            [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.sync :as sync]
            [com.hjsoft.roadside.website.ui.hooks :refer [use-user-location]]
            [com.hjsoft.roadside.website.ui.map :refer [leaflet-map]]
            [com.hjsoft.roadside.website.ui.stands :refer [stands-list product-list]]
            [com.hjsoft.roadside.website.ui.forms :refer [stand-form settings-dialog]]
            [com.hjsoft.roadside.website.ui.layout :refer [header fixed-header notification-toast]]
            [cljs.core.async :refer [go <!]]))

(def initial-zoom-level 11)

(defn use-local-persistence
  [{:keys [stands settings map-center]}]
  (hooks/use-effect
   [stands settings map-center]
   (sync/save-local-data! stands settings map-center)))

(defn use-location-sync
  [dispatch {:keys [location]}]
  (hooks/use-effect
   [location]
   (when location
     (dispatch [:set-map-center location]))))

(defn use-remote-sync
  [app-state dispatch]
  (let [{:keys [stands is-synced settings]} app-state]
    ;; Sync with Remote API
    (hooks/use-effect
     [stands is-synced settings]
     (sync/save-remote-stands! app-state dispatch))

    ;; Fetch from Remote API on settings change
    (hooks/use-effect
     [settings]
     (sync/fetch-remote-stands! app-state dispatch))))

(defn use-initial-location
  [{:keys [get-location]}]
  (hooks/use-effect :once (get-location)))

(defnc app []
  (let [[app-state dispatch] (hooks/use-reducer
                              state/app-reducer
                              state/initial-app-state)
        {:keys [stands map-center show-form show-settings-dialog]} app-state

        user-location (use-user-location)

        _ (use-local-persistence app-state)
        _ (use-location-sync dispatch user-location)
        _ (use-remote-sync app-state dispatch)
        _ (use-initial-location user-location)

        filtered-stands (hooks/use-memo
                         [stands (:product-filter app-state)]
                         (state/select-filtered-stands app-state))

        set-coordinate-form-data (hooks/use-callback
                                  [dispatch]
                                  (fn [c]
                                    (dispatch
                                     [:set-map-center
                                      (utils/parse-coordinates c)])))]

    (d/div
     {:class "app-container"}
     ($ (.-Provider state/app-context)
        {:value {:state app-state
                 :dispatch dispatch
                 :user-location user-location}}
        (<>
         ($ notification-toast)
         ($ fixed-header
            ($ header)
            ($ leaflet-map
               {:div-id "map-container"
                :stands filtered-stands
                :zoom-level initial-zoom-level
                :set-coordinate-form-data set-coordinate-form-data}))
         (d/div
          {:class "content"}
          (d/div
           {:class "main-actions"}
           (d/button
            {:class "add-stand-btn"
             :onClick #(dispatch [:open-add-form])}
            "Add Stand")
           (d/div
            {:class "map-actions-right"}
            (when (and (:error user-location) (string? (:error user-location)))
              (d/p {:class "error-message"} (:error user-location)))
            (d/button
             {:type "button"
              :class "location-btn"
              :onClick #((:get-location user-location))}
             "\u2316")))
          ($ product-list {:stands stands})
          (when show-form ($ stand-form))
          ($ stands-list {:stands filtered-stands})
          (d/button
           {:class "settings-btn"
            :onClick #(dispatch [:set-show-settings-dialog true])}
           "\u2699")
          (when show-settings-dialog ($ settings-dialog))))))))

(defn init []
  (let [root (.createRoot rdom (js/document.getElementById "app"))]
    (.render root ($ app))))
