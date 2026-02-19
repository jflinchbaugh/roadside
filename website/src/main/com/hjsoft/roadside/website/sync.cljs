(ns com.hjsoft.roadside.website.sync
  (:require [com.hjsoft.roadside.website.api :as api]
            [com.hjsoft.roadside.website.storage :as storage]
            [taoensso.telemere :as tel]
            [cljs.core.async :refer [go <!]]))

(defn save-local-data! [stands settings map-center]
  (storage/set-item! "roadside-stands" stands)
  (storage/set-item! "roadside-settings" settings)
  (storage/set-item! "roadside-map-center" map-center))

(defn- has-credentials? [settings]
  (and (seq (:resource settings))
       (seq (:user settings))
       (seq (:password settings))))

(defn- notify! [dispatch type message]
  (dispatch [:set-notification {:type type :message message}]))

(defn fetch-remote-stands!
  [{:keys [settings]} dispatch]
  (when (has-credentials? settings)
    (go
      (let [{:keys [success data error]} (<! (api/fetch-stands
                                              (:resource settings)
                                              (:user settings)
                                              (:password settings)))]
        (if success
          (do
            (dispatch [:set-stands data])
            (dispatch [:set-is-synced true])
            (notify! dispatch :success "Stands synced!"))
          (do
            (tel/log! :error {:msg "Failed to fetch stands" :error error})
            (notify! dispatch :error (str "Sync failed: " error))))))))

(defn sync-create-stand!
  [{:keys [settings]} dispatch stand]
  (when (has-credentials? settings)
    (go
      (let [{:keys [success error]} (<! (api/create-stand
                                         (:resource settings)
                                         (:user settings)
                                         (:password settings)
                                         stand))]
        (if success
          (notify! dispatch :success "Stand added!")
          (do
            (tel/log! :error {:msg "Failed to create stand" :error error})
            (notify! dispatch :error (str "Create failed: " error))))))))

(defn sync-update-stand!
  [{:keys [settings]} dispatch stand]
  (when (has-credentials? settings)
    (go
      (let [{:keys [success error]} (<! (api/update-stand
                                         (:resource settings)
                                         (:user settings)
                                         (:password settings)
                                         stand))]
        (if success
          (notify! dispatch :success "Stand updated!")
          (do
            (tel/log! :error {:msg "Failed to update stand" :error error})
            (notify! dispatch :error (str "Update failed: " error))))))))

(defn sync-delete-stand!
  [{:keys [settings]} dispatch stand-id]
  (when (has-credentials? settings)
    (go
      (let [{:keys [success error]} (<! (api/delete-stand
                                         (:resource settings)
                                         (:user settings)
                                         (:password settings)
                                         stand-id))]
        (if success
          (notify! dispatch :success "Stand deleted!")
          (do
            (tel/log! :error {:msg "Failed to delete stand" :error error})
            (notify! dispatch :error (str "Delete failed: " error))))))))
