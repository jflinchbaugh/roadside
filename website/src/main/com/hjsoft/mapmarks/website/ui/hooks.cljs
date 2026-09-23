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
     :extend-mark! (fn [mark & [days]]
                     (controller/extend-mark! app-state dispatch mark days))
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
        watch-id-ref (hooks/use-ref nil)
        callbacks-ref (hooks/use-ref [])
        clear-current-watch! (fn []
                               (when-let [w-id @watch-id-ref]
                                 (when (and (not= geo :not-supported)
                                            (fn? (.-clearWatch ^js geo)))
                                   (.clearWatch ^js geo w-id))
                                 (reset! watch-id-ref nil)))
        get-location (hooks/use-callback
                      [dispatch geo location]
                      (fn [& [on-success on-error]]
                        (when (or on-success on-error)
                          (swap! callbacks-ref conj
                                 {:on-success on-success
                                  :on-error on-error}))
                        (when (and dispatch
                                   (or (not (exists? js/window))
                                       (empty? (.. js/window -location -hash))))
                          (dispatch [:set-selected-mark nil]))
                        (reset! cancelled-ref false)
                        (set-error nil)
                        (if (= geo :not-supported)
                          (do
                            (tel/log! :warn {:geolocation :not-supported})
                            (reset! locating-ref false)
                            (set-is-locating false)
                            (set-error "Geolocation not supported.")
                            (let [cbs @callbacks-ref]
                              (reset! callbacks-ref [])
                              (doseq [{:keys [on-error]} cbs]
                                (when (fn? on-error)
                                  (on-error "Geolocation not supported.")))))
                          (let [on-geoposition-success
                                (fn [position]
                                  (tel/log! :debug {:geolocation :success})
                                  (reset! locating-ref false)
                                  (set-is-locating false)
                                  (when-not @cancelled-ref
                                    (let [coords (.-coords position)
                                          loc [(.-latitude coords)
                                               (.-longitude coords)]
                                          cbs @callbacks-ref]
                                      (reset! callbacks-ref [])
                                      (set-location loc)
                                      (doseq [{:keys [on-success]} cbs]
                                        (when (fn? on-success)
                                          (on-success loc))))))
                                on-geoposition-error
                                (fn [err]
                                  (let [msg (.-message err)
                                        cbs @callbacks-ref]
                                    (tel/log! :error
                                      {:geolocation {:error msg}})
                                    (clear-current-watch!)
                                    (reset! locating-ref false)
                                    (set-is-locating false)
                                    (reset! callbacks-ref [])
                                    (when-not @cancelled-ref
                                      (set-error
                                       (str "Unable to retrieve location: "
                                            msg))
                                      (doseq [{:keys [on-error]} cbs]
                                        (when (fn? on-error)
                                          (on-error msg))))))
                                opts #js {:enableHighAccuracy false
                                          :timeout 15000
                                          :maximumAge 30000}]
                            (if-let [_w-id @watch-id-ref]
                              (when-let [loc location]
                                (let [cbs @callbacks-ref]
                                  (reset! callbacks-ref [])
                                  (doseq [{:keys [on-success]} cbs]
                                    (when (fn? on-success)
                                      (on-success loc)))))
                              (do
                                (tel/log! :debug {:geolocation :starting})
                                (reset! locating-ref true)
                                (when (nil? location)
                                  (set-is-locating true))
                                (if (fn? (.-watchPosition ^js geo))
                                  (let [w-id (.watchPosition
                                              ^js geo
                                              on-geoposition-success
                                              on-geoposition-error
                                              opts)]
                                    (reset! watch-id-ref w-id))
                                  (if (fn? (.-getCurrentPosition ^js geo))
                                    (.getCurrentPosition
                                     ^js geo
                                     on-geoposition-success
                                     on-geoposition-error
                                     opts)
                                    (do
                                      (tel/log! :warn
                                        {:geolocation :not-supported})
                                      (reset! locating-ref false)
                                      (set-is-locating false)
                                      (set-error "Geolocation not supported.")
                                      (let [cbs @callbacks-ref]
                                        (reset! callbacks-ref [])
                                        (doseq [{:keys [on-error]} cbs]
                                          (when (fn? on-error)
                                            (on-error
                                             "Geolocation not supported.")))))))))))))
        cancel-location (hooks/use-callback
                         :once
                         (fn []
                           (tel/log! :debug {:geolocation :cancelled})
                           (reset! cancelled-ref true)
                           (reset! locating-ref false)
                           (set-is-locating false)
                           (clear-current-watch!)))]
    (hooks/use-effect
     :once
     (fn []
       (fn []
         (clear-current-watch!))))
    (hooks/use-memo
     [location error is-locating get-location cancel-location]
     {:location location
      :error error
      :is-locating is-locating
      :get-location get-location
      :cancel-location cancel-location})))
