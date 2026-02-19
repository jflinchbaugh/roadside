(ns com.hjsoft.roadside.website.core
  (:require ["react-dom/client" :as rdom]
            [helix.core :refer [defnc $ <>]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.sync :as sync]
            [com.hjsoft.roadside.website.ui.hooks :refer [use-user-location]]
            [com.hjsoft.roadside.website.ui.map :refer [leaflet-map]]
            [com.hjsoft.roadside.website.ui.stands
             :refer [stands-list product-list]]
            [com.hjsoft.roadside.website.ui.forms
             :refer [stand-form settings-dialog]]
            [com.hjsoft.roadside.website.ui.layout
             :refer [header fixed-header notification-toast]]))

(def initial-zoom-level 11)

(defn use-app-side-effects
  [app-state dispatch user-location]
  (let [{:keys [stands settings map-center]} app-state
        {:keys [location get-location]} user-location]

    ;; Local persistence
    (hooks/use-effect
     [stands settings map-center]
     (sync/save-local-data! stands settings map-center))

    ;; Location sync to map center
    (hooks/use-effect
     [location]
     (when location
       (dispatch [:set-map-center location])))

    ;; Fetch from Remote API on settings change
    (hooks/use-effect
     [settings]
     (sync/fetch-remote-stands! app-state dispatch))

    ;; Initial location fetch
    (hooks/use-effect :once (get-location))))

(defnc app []
  (let [[app-state dispatch] (hooks/use-reducer
                              state/app-reducer
                              state/initial-app-state)
        {:keys [stands]} app-state

        [show-form set-show-form] (hooks/use-state false)
        [editing-stand set-editing-stand] (hooks/use-state nil)
        [show-settings-dialog set-show-settings-dialog] (hooks/use-state false)

        user-location (use-user-location)

        _ (use-app-side-effects app-state dispatch user-location)

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
                 :user-location user-location
                 :ui {:show-form show-form
                      :set-show-form set-show-form
                      :editing-stand editing-stand
                      :set-editing-stand set-editing-stand
                      :show-settings-dialog show-settings-dialog
                      :set-show-settings-dialog set-show-settings-dialog}}}
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
             :onClick #(do
                         (set-editing-stand nil)
                         (set-show-form true))}
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
            :onClick #(set-show-settings-dialog true)}
           "\u2699")
          (when show-settings-dialog ($ settings-dialog))))))))

(defn init []
  (let [root (.createRoot rdom (js/document.getElementById "app"))]
    (.render root ($ app))))
