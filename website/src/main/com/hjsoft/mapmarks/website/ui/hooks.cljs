(ns com.hjsoft.mapmarks.website.ui.hooks
  (:require [helix.hooks :as hooks]
            [taoensso.telemere :as tel]
            [com.hjsoft.mapmarks.website.state :as state]
            [com.hjsoft.mapmarks.website.controller :as controller]))

(defn use-actions []
  (let [app-state (state/use-app-state)
        dispatch (state/use-dispatch)
        {:keys [set-show-form set-editing-mark]} (state/use-ui)]
    {:create-mark! (fn [form-data]
                      (when (controller/create-mark!
                             app-state dispatch form-data)
                        (set-show-form false)))
     :update-mark! (fn [form-data editing-mark]
                      (when (controller/update-mark!
                             app-state dispatch form-data editing-mark)
                        (set-show-form false)))
     :delete-mark! (fn [mark]
                      (controller/delete-mark! app-state dispatch mark))
     :vote-mark! (fn [mark value]
                    (controller/vote-mark! app-state dispatch mark value))
     :lookup-address! (fn [on-update address-data]
                        (controller/lookup-address! app-state dispatch on-update address-data))
     :reverse-lookup! (fn [on-update lat lng]
                        (controller/reverse-lookup! app-state dispatch on-update lat lng))
     :cancel-form! (fn []
                     (set-show-form false)
                     (set-editing-mark nil))}))

(defn use-escape-key [on-escape]
  (hooks/use-effect
   :once
   (let [handle-keydown (fn [e]
                          (when (= (.-key e) "Escape")
                            (on-escape)))]
     (.addEventListener js/document "keydown" handle-keydown)
     (fn []
       (.removeEventListener js/document "keydown" handle-keydown)))))

(defn use-user-location [dispatch geolocation]
  (let [geo (if (nil? geolocation) :not-supported geolocation)
        [location set-location] (hooks/use-state nil)
         [error set-error] (hooks/use-state nil)
         [is-locating set-is-locating] (hooks/use-state false)
         locating-ref (hooks/use-ref false)
         cancelled-ref (hooks/use-ref false)
         get-location (hooks/use-callback
                       [dispatch geo]
                       (fn [& [on-success on-error]]
                         (let [on-geoposition-success (fn [position]
                                                        (tel/log! :debug
                                                          {:geolocation :success})
                                                        (reset! locating-ref false)
                                                        (set-is-locating false)
                                                        (when-not @cancelled-ref
                                                          (let [coords (.-coords position)
                                                                loc [(.-latitude coords)
                                                                     (.-longitude coords)]]
                                                            (set-location loc)
                                                            (when (fn? on-success) (on-success loc)))))
                               on-geoposition-error (fn [err]
                                                      (let [msg (.-message err)]
                                                        (tel/log! :error {:geolocation {:error msg}})
                                                        (reset! locating-ref false)
                                                        (set-is-locating false)
                                                        (when-not @cancelled-ref
                                                          (set-error (str "Unable to retrieve location: " msg))
                                                          (when (fn? on-error) (on-error msg)))))]
                           (when-not @locating-ref
                             (tel/log! :debug {:geolocation :starting})
                             (when (and dispatch
                                        (or (not (exists? js/window))
                                            (empty? (.. js/window -location -hash))))
                               (dispatch [:set-selected-mark nil]))
                             (reset! locating-ref true)
                             (reset! cancelled-ref false)
                             (set-is-locating true)
                             (set-error nil)
                             (if (not= geo :not-supported)
                               (.getCurrentPosition
                                geo
                                on-geoposition-success
                                on-geoposition-error
                                #js {:enableHighAccuracy false
                                     :timeout 15000
                                     :maximumAge 30000})
                               (do
                                 (tel/log! :warn {:geolocation :not-supported})
                                 (reset! locating-ref false)
                                 (set-is-locating false)
                                 (set-error "Geolocation not supported.")
                                 (when (fn? on-error)
                                   (on-error "Geolocation not supported."))))))))
        cancel-location (hooks/use-callback
                         :once
                         (fn []
                           (tel/log! :debug {:geolocation :cancelled})
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
