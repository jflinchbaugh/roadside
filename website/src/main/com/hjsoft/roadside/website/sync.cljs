(ns com.hjsoft.roadside.website.sync
  (:require [com.hjsoft.roadside.website.api :as api]
            [com.hjsoft.roadside.website.storage :as storage]
            [taoensso.telemere :as tel]
            [cljs.core.async :refer [go <!]]))

(defn save-local-data! [stands settings map-center]
  (storage/set-item! "roadside-stands" stands)
  (storage/set-item! "roadside-settings" settings)
  (storage/set-item! "roadside-map-center" map-center))

(defn fetch-remote-stands!
  [{:keys [settings]} dispatch show-notification]
  (let [{:keys [resource user password]} settings]
    (when (and (seq resource) (seq user) (seq password))
      (go
        (let [{:keys [success data error]} (<! (api/fetch-stands
                                                 resource
                                                 user
                                                 password))]
          (if success
            (do
              (dispatch [:set-stands data])
              (dispatch [:set-is-synced true])
              (show-notification :success "Stands synced!"))
            (do
              (tel/log! :error {:msg "Failed to fetch stands" :error error})
              (show-notification :error (str "Sync failed: " error)))))))))

(defn save-remote-stands!
  [{:keys [stands settings is-synced]} show-notification]
  (let [{:keys [resource user password]} settings]
    (when (and (seq resource) (seq user) (seq password) is-synced)
      (go
        (let [{:keys [success error]} (<! (api/save-stands
                                            resource
                                            user
                                            password
                                            stands))]
          (if success
            (show-notification :success "Stands saved!")
            (do
              (tel/log! :error {:msg "Failed to save stands" :error error})
              (show-notification :error (str "Save failed: " error)))))))))
