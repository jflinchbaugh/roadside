(ns com.hjsoft.roadside.website.controller
  (:require [com.hjsoft.roadside.website.api :as api]
            [com.hjsoft.roadside.website.storage :as storage]
            [com.hjsoft.roadside.website.domain.stand :as stand-domain]
            [com.hjsoft.roadside.website.utils :as utils]
            [taoensso.telemere :as tel]
            [clojure.string :as str]
            [cljs.core.async :refer [go <!]]))

(defn save-local-data! [stands settings map-center]
  (storage/set-item! "roadside-stands" stands)
  (storage/set-item! "roadside-settings" settings)
  (storage/set-item! "roadside-map-center" map-center))

(defn- has-credentials? [settings]
  (and (seq (:user settings))
       (seq (:password settings))))

(defn- notify!
  ([dispatch type message]
   (notify! dispatch type message nil))
  ([dispatch type message stand-id]
   (dispatch [:set-notification {:type type :message message :stand-id stand-id}])))

(defn fetch-remote-stands!
  [{:keys [settings]} dispatch]
  (when (has-credentials? settings)
    (go
      (let [{:keys [success data error]} (<! (api/fetch-stands
                                              (:user settings)
                                              (:password settings)))]
        (if success
          (do
            (dispatch [:set-stands data])
            (dispatch [:set-is-synced true])
            (notify! dispatch :success "Stands refreshed"))
          (do
            (tel/log! :error {:msg "Failed to fetch stands" :error error})
            (notify! dispatch :error (str "Sync failed: " error))))))))

(defn- remote-create-stand!
  [{:keys [settings]} dispatch stand]
  (when (has-credentials? settings)
    (go
      (let [{:keys [success error]} (<! (api/create-stand
                                         (:user settings)
                                         (:password settings)
                                         stand))]
        (if success
          (notify! dispatch :success "Stand added!" (:id stand))
          (do
            (tel/log! :error {:msg "Failed to create stand" :error error})
            (notify! dispatch :error (str "Create failed: " error) (:id stand))))))))

(defn- remote-update-stand!
  [{:keys [settings]} dispatch stand]
  (when (has-credentials? settings)
    (go
      (let [{:keys [success error]} (<! (api/update-stand
                                         (:user settings)
                                         (:password settings)
                                         stand))]
        (if success
          (notify! dispatch :success "Stand updated!" (:id stand))
          (do
            (tel/log! :error {:msg "Failed to update stand" :error error})
            (notify! dispatch :error (str "Update failed: " error) (:id stand))))))))

(defn- remote-delete-stand!
  [{:keys [settings]} dispatch stand-id]
  (when (has-credentials? settings)
    (go
      (let [{:keys [success error]} (<! (api/delete-stand
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
  (let [creator (get-in app-state [:settings :user])
        {:keys [success
                stands
                error
                processed-data]} (stand-domain/add-stand
                                   form-data
                                   (:stands app-state)
                                   creator)]
    (if success
      (do
        (dispatch [:set-stands stands])
        (dispatch [:set-selected-stand processed-data])
        (when-let [coords (utils/parse-coordinates (:coordinate processed-data))]
          (dispatch [:set-map-center coords]))
        (remote-create-stand! app-state dispatch processed-data)
        (try
          (when (and (empty? (str/trim (or (:name processed-data) "")))
                     (empty? (:products processed-data)))
            (utils/show-system-notification
             "Stand Added"
             {:body "Remember to complete the recently added Roadside Stand."
              :icon "images/apples.png"}))
          (catch :default e
            (tel/log! :error {:msg "Failed to show system notification" :error e})))
        true)
      (do
        (notify! dispatch :error error)
        false))))

(defn update-stand! [app-state dispatch form-data editing-stand]
  (let [creator (get-in app-state [:settings :user])
        {:keys [success
                stands
                error
                processed-data]} (stand-domain/edit-stand
                                   form-data
                                   (:stands app-state)
                                   editing-stand
                                   creator)]
    (if success
      (do
        (dispatch [:set-stands stands])
        (dispatch [:set-selected-stand processed-data])
        (when-let [coords (utils/parse-coordinates (:coordinate processed-data))]
          (dispatch [:set-map-center coords]))
        (remote-update-stand! app-state dispatch processed-data)
        true)
      (do
        (notify! dispatch :error error (:id editing-stand))
        false))))
(defn delete-stand! [app-state dispatch stand]
  (dispatch [:remove-stand stand])
  (remote-delete-stand! app-state dispatch (:id stand)))

(defn lookup-address! [dispatch on-update address-data]
  (let [address (str/join ", " (remove empty? [(:address address-data)
                                               (:town address-data)
                                               (:state address-data)]))]
    (if (empty? address)
      (notify! dispatch :error "Address is empty!")
      (go
        (notify! dispatch :info "Looking up address...")
        (let [{:keys [success lat lng error]} (<! (api/geocode-address address))]
          (if success
            (do
              (on-update [:update-field [:coordinate (str lat ", " lng)]])
              (dispatch [:set-map-center [lat lng]])
              (notify! dispatch :success "Address found!"))
            (notify! dispatch :error (str "Geocoding failed: " error))))))))
