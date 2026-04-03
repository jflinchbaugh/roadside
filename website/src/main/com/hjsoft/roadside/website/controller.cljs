(ns com.hjsoft.roadside.website.controller
  (:require [com.hjsoft.roadside.website.api :as api]
            [com.hjsoft.roadside.website.storage :as storage]
            [com.hjsoft.roadside.website.domain.stand :as stand-domain]
            [com.hjsoft.roadside.website.utils :as utils]
            [taoensso.telemere :as tel]
            [clojure.string :as str]
            [cljs.core.async :refer [go <!]]))

(def default-deps
  {:fetch-stands api/fetch-stands
   :create-stand api/create-stand
   :update-stand api/update-stand
   :delete-stand api/delete-stand
   :geocode-address api/geocode-address
   :reverse-geocode api/reverse-geocode})

(defn save-local-data! [stands settings map-center map-zoom last-sync]
  (storage/set-item! "roadside-stands" stands)
  (storage/set-item! "roadside-settings" settings)
  (storage/set-item! "roadside-map-center" map-center)
  (storage/set-item! "roadside-map-zoom" map-zoom)
  (storage/set-item! "roadside-last-sync" last-sync))

(defn- has-credentials? [settings]
  (and (seq (:user settings))
       (seq (:password settings))))

(defn- format-error [message]
  (cond
    (string? message) message
    (seq message) (str/join ", " message)
    :else (str " else " message)))

(defn- notify!
  ([dispatch type message]
   (notify! dispatch type message nil))
  ([dispatch type message stand-id]
   (dispatch [:set-notification {:type type
                                 :message message
                                 :stand-id stand-id}])))

(defn fetch-remote-stands!
  ([app-state dispatch]
   (fetch-remote-stands! app-state dispatch default-deps))
  ([{:keys [settings map-center last-sync]} dispatch {:keys [fetch-stands]}]
   (dispatch [:set-loading-stands true])
   (go
     (let [[lat lng] map-center
           {:keys [success data error]} (<! (fetch-stands
                                             (:user settings)
                                             (:password settings)
                                             lat lng
                                             last-sync))]
       (dispatch [:set-loading-stands false])
       (if success
         (do
           (dispatch [:sync-stands {:stands (:stands data)
                                    :deleted-ids (:deleted-ids data)
                                    :last-sync (:new-sync data)}])
           (dispatch [:set-is-synced true]))
         (do
           (tel/log! :error {:msg "Failed to fetch stands" :error error})
           (when (has-credentials? settings)
             (notify!
               dispatch
               :error
               (str "Sync failed: " (format-error error))))))))))

(defn- remote-create-stand!
  [{:keys [settings]} dispatch stand {:keys [create-stand]}]
  (when (has-credentials? settings)
    (go
      (let [{:keys [success error]} (<! (create-stand
                                         (:user settings)
                                         (:password settings)
                                         stand))]
        (if success
          (notify! dispatch :success "Stand added!" (:id stand))
          (do
            (tel/log! :error {:msg "Failed to create stand" :error error})
            (notify!
              dispatch
              :error
              (str "Create failed: " (format-error error))
              (:id stand))))))))

(defn- remote-update-stand!
  [{:keys [settings]} dispatch stand {:keys [update-stand]}]
  (when (has-credentials? settings)
    (go
      (let [{:keys [success error]} (<! (update-stand
                                         (:user settings)
                                         (:password settings)
                                         stand))]
        (if success
          (notify! dispatch :success "Stand updated!" (:id stand))
          (do
            (tel/log! :error {:msg "Failed to update stand" :error error})
            (notify!
              dispatch
              :error
              (str "Update failed: " (format-error error)) (:id stand))))))))

(defn- remote-delete-stand!
  [{:keys [settings]} dispatch stand-id {:keys [delete-stand]}]
  (when (has-credentials? settings)
    (go
      (let [{:keys [success error]} (<! (delete-stand
                                         (:user settings)
                                         (:password settings)
                                         stand-id))]
        (if success
          (notify! dispatch :success "Stand deleted!")
          (do
            (tel/log! :error {:msg "Failed to delete stand" :error error})
            (notify!
              dispatch
              :error
              (str "Delete failed: " (format-error error)))))))))

(defn upload-all-stands!
  ([app-state dispatch]
   (upload-all-stands! app-state dispatch default-deps))
  ([{:keys [stands settings]} dispatch {:keys [create-stand]}]
   (if-not (has-credentials? settings)
     (notify! dispatch :error "Authentication required to upload!")
     (go
       (notify! dispatch :info (str "Uploading " (count stands) " stands..."))
       (let [results (atom [])]
         (doseq [stand stands]
           (let [res (<! (create-stand (:user settings) (:password settings) stand))]
             (swap! results conj res)))
         (let [success-count (count (filter :success @results))
               fail-count (- (count stands) success-count)]
           (if (pos? fail-count)
             (notify!
               dispatch
               :error
               (str
                 "Upload finished: "
                 success-count
                 " successes, "
                 fail-count
                 " failures."))
             (notify!
               dispatch
               :success
               (str
                 "Successfully uploaded "
                 success-count
                 " stands!")))))))))

;; Controller Intent Functions

(defn create-stand!
  ([app-state dispatch form-data]
   (create-stand! app-state dispatch form-data default-deps))
  ([app-state dispatch form-data deps]
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
         (dispatch [:set-map-center [(:lat processed-data) (:lon processed-data)]])
         (remote-create-stand! app-state dispatch processed-data deps)
         true)
       (do
         (notify! dispatch :error (format-error error))
         false)))))

(defn update-stand!
  ([app-state dispatch form-data editing-stand]
   (update-stand! app-state dispatch form-data editing-stand default-deps))
  ([app-state dispatch form-data editing-stand deps]
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
         (dispatch [:set-map-center [(:lat processed-data) (:lon processed-data)]])
         (remote-update-stand! app-state dispatch processed-data deps)
         true)
       (do
         (notify! dispatch :error (format-error error) (:id editing-stand))
         false)))))

(defn delete-stand!
  ([app-state dispatch stand]
   (delete-stand! app-state dispatch stand default-deps))
  ([app-state dispatch stand deps]
   (dispatch [:remove-stand stand])
   (remote-delete-stand! app-state dispatch (:id stand) deps)))

(defn lookup-address!
  ([app-state dispatch on-update address-data]
   (lookup-address! app-state dispatch on-update address-data default-deps))
  ([app-state dispatch on-update address-data {:keys [geocode-address]}]
   (let [settings (:settings app-state)
         user (:user settings)
         password (:password settings)
         address (str/join ", " (remove empty? [(:address address-data)
                                                (:town address-data)
                                                (:state address-data)]))]
     (if (or (empty? user) (empty? password))
       (notify! dispatch :error "Authentication required for address lookup!")
       (if (empty? address)
         (notify! dispatch :error "Address is empty!")
         (go
           (notify! dispatch :info "Looking up address...")
           (let [{:keys [success lat lng error]} (<!
                                                   (geocode-address
                                                     user
                                                     password
                                                     address))]
             (if success
               (do
                 (on-update [:update-field [:coordinate (str lat ", " lng)]])
                 (dispatch [:set-map-center [lat lng]])
                 (notify! dispatch :success "Address found!"))
               (notify!
                 dispatch
                 :error
                 (str "Geocoding failed: " (format-error error)))))))))))

(defn reverse-lookup!
  ([app-state dispatch on-update lat lng]
   (reverse-lookup! app-state dispatch on-update lat lng default-deps))
  ([app-state dispatch on-update lat lng {:keys [reverse-geocode]}]
   (let [settings (:settings app-state)
         user (:user settings)
         password (:password settings)]
     (if (or (empty? user) (empty? password))
       (notify! dispatch :error "Authentication required for address lookup!")
       (go
         (notify! dispatch :info "Determining address...")
         (let [{:keys [success data error]} (<! (reverse-geocode user password lat lng))]
           (if success
             (let [addr (:address data)
                   road (:road addr)
                   house-number (:house_number addr)
                   town (or (:city addr) (:town addr) (:village addr) (:suburb addr))
                   state (:state addr)
                   full-address (str (when house-number (str house-number " ")) road)]
               (when (seq full-address)
                 (on-update [:update-field [:address full-address]]))
               (when town
                 (on-update [:update-field [:town town]]))
               (when state
                 (on-update [:update-field [:state state]]))
               (notify! dispatch :success "Address determined!"))
             (notify!
               dispatch
               :error
               (str "Reverse geocoding failed: " (format-error error))))))))))
