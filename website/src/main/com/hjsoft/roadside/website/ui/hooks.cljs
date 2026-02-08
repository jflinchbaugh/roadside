(ns com.hjsoft.roadside.website.ui.hooks
  (:require [helix.hooks :as hooks]
            [taoensso.telemere :as tel]
            [com.hjsoft.roadside.website.utils :as utils]))

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
                                 (when (fn? on-success) (on-success loc)))))
                           (fn [err]
                             (when-not @cancelled-ref
                               (let [msg (.-message err)]
                                 (tel/log! :error {:failed-location msg})
                                 (set-error "Unable to retrieve location.")
                                 (set-is-locating false)
                                 (when (fn? on-error) (on-error msg))))))
                          (do
                            (set-error "Geolocation not supported.")
                            (set-is-locating false)
                            (when (fn? on-error) (on-error "Geolocation not supported."))))))
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
