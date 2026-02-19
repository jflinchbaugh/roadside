(ns com.hjsoft.roadside.website.controller
  (:require [com.hjsoft.roadside.website.api :as api]
            [com.hjsoft.roadside.website.storage :as storage]
            [com.hjsoft.roadside.website.domain.stand :as stand-domain]
            [com.hjsoft.roadside.website.utils :as utils]
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

(defn- remote-create-stand!
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

(defn- remote-update-stand!
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

(defn- remote-delete-stand!
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

;; Controller Intent Functions

(defn create-stand! [app-state dispatch form-data]
  (let [{:keys [success stands error processed-data]} (stand-domain/process-stand-form
                                                       form-data
                                                       (:stands app-state)
                                                       nil)]
    (if success
      (do
        (dispatch [:set-stands stands])
        (dispatch [:set-selected-stand processed-data])
        (when-let [coords (utils/parse-coordinates (:coordinate processed-data))]
          (dispatch [:set-map-center coords]))
        (remote-create-stand! app-state dispatch processed-data)
        true)
      (do
        (notify! dispatch :error error)
        false))))

(defn update-stand! [app-state dispatch form-data editing-stand]
  (let [{:keys [success stands error processed-data]} (stand-domain/process-stand-form
                                                       form-data
                                                       (:stands app-state)
                                                       editing-stand)]
    (if success
      (do
        (dispatch [:set-stands stands])
        (dispatch [:set-selected-stand processed-data])
        (when-let [coords (utils/parse-coordinates (:coordinate processed-data))]
          (dispatch [:set-map-center coords]))
        (remote-update-stand! app-state dispatch processed-data)
        true)
      (do
        (notify! dispatch :error error)
        false))))

(defn delete-stand! [app-state dispatch stand]
  (dispatch [:remove-stand stand])
  (remote-delete-stand! app-state dispatch (:id stand)))
