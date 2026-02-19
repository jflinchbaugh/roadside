(ns com.hjsoft.roadside.website.ui.hooks
  (:require [helix.hooks :as hooks]
            [taoensso.telemere :as tel]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.controller :as controller]))

(defn use-actions []
  (let [app-state (state/use-app-state)
        dispatch (state/use-dispatch)
        {:keys [set-show-form set-editing-stand]} (state/use-ui)]
    {:create-stand! (fn [form-data]
                      (when (controller/create-stand!
                             app-state dispatch form-data)
                        (set-show-form false)))
     :update-stand! (fn [form-data editing-stand]
                      (when (controller/update-stand!
                             app-state dispatch form-data editing-stand)
                        (set-show-form false)))
     :delete-stand! (fn [stand]
                      (controller/delete-stand! app-state dispatch stand))
     :cancel-form! (fn []
                     (set-show-form false)
                     (set-editing-stand nil))}))

(defn use-escape-key [on-escape]
  (hooks/use-effect
   :once
   (let [handle-keydown (fn [e]
                          (when (= (.-key e) "Escape")
                            (on-escape)))]
     (.addEventListener js/document "keydown" handle-keydown)
     (fn []
       (.removeEventListener js/document "keydown" handle-keydown)))))

(defn use-user-location []
  (let [[location set-location] (hooks/use-state nil)
        [error set-error] (hooks/use-state nil)
        [is-locating set-is-locating] (hooks/use-state false)
        locating-ref (hooks/use-ref false)
        cancelled-ref (hooks/use-ref false)
        get-location (hooks/use-callback
                      :once
                      (fn [& [on-success on-error]]
                        (when-not @locating-ref
                          (reset! locating-ref true)
                          (reset! cancelled-ref false)
                          (set-is-locating true)
                          (set-error nil)
                          (if (and
                               (exists? js/navigator)
                               (exists? js/navigator.geolocation))
                            (js/navigator.geolocation.getCurrentPosition
                             (fn [position]
                               (reset! locating-ref false)
                               (set-is-locating false)
                               (when-not @cancelled-ref
                                 (let [coords (.-coords position)
                                       loc [(.-latitude coords)
                                            (.-longitude coords)]]
                                   (set-location loc)
                                   (when (fn? on-success) (on-success loc)))))
                             (fn [err]
                               (reset! locating-ref false)
                               (set-is-locating false)
                               (when-not @cancelled-ref
                                 (let [msg (.-message err)]
                                   (tel/log! :error {:failed-location msg})
                                   (set-error "Unable to retrieve location.")
                                   (when (fn? on-error) (on-error msg)))))
                             #js {:enableHighAccuracy false
                                  :timeout 20000
                                  :maximumAge 30000})
                            (do
                              (reset! locating-ref false)
                              (set-is-locating false)
                              (set-error "Geolocation not supported.")
                              (when (fn? on-error)
                                (on-error "Geolocation not supported.")))))))
        cancel-location (hooks/use-callback
                         :once
                         (fn []
                           (reset! cancelled-ref true)
                           (reset! locating-ref false)
                           (set-is-locating false)))]
    (hooks/use-memo
     [location error is-locating get-location cancel-location]
     {:location location
      :error error
      :is-locating is-locating
      :get-location get-location
      :cancel-location cancel-location})))
