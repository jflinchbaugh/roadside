(ns com.hjsoft.mapmarks.website.controller
  (:require [com.hjsoft.mapmarks.website.api :as api]
            [com.hjsoft.mapmarks.website.storage :as storage]
            [com.hjsoft.mapmarks.website.config :as config]
            [com.hjsoft.mapmarks.website.domain.mark :as mark-domain]
            [taoensso.telemere :as tel]
            [clojure.string :as str]
            [cljs.core.async :refer [go <!]]))

(def default-deps
  {:fetch-marks api/fetch-marks
   :create-mark api/create-mark
   :update-mark api/update-mark
   :delete-mark api/delete-mark
   :vote-mark api/vote-mark
   :geocode-address api/geocode-address
   :reverse-geocode api/reverse-geocode})

(defn save-local-data! [marks settings map-center map-zoom last-sync]
  (let [site-name (:site config/config)]
    (storage/set-item! (str site-name "-marks") marks)
    (storage/set-item! (str site-name "-settings") settings)
    (storage/set-item! (str site-name "-map-center") map-center)
    (storage/set-item! (str site-name "-map-zoom") map-zoom)
    (storage/set-item! (str site-name "-last-sync") last-sync)))

(defn- has-credentials? [settings]
  (and (seq (:user settings))
       (seq (:password settings))))

(defn- remote-allowed? [settings]
  (not (:local-only? settings)))

(defn- format-error [message]
  (cond
    (string? message) message
    (seq message) (str/join ", " message)
    :else (str " else " message)))

(defn- notify!
  ([dispatch type message]
   (notify! dispatch type message nil))
  ([dispatch type message mark-id]
   (dispatch [:set-notification {:type type
                                 :message message
                                 :mark-id mark-id}])))

(defn fetch-remote-marks!
  ([app-state dispatch]
   (fetch-remote-marks! app-state dispatch default-deps))
  ([{:keys [settings map-center last-sync config]} dispatch {:keys [fetch-marks]}]
   (when (remote-allowed? settings)
     (dispatch [:set-loading-marks true])
     (go
       (let [[lat lng] map-center
             site (:site config)
             {:keys [success data error]} (<! (fetch-marks
                                               site
                                               (:user settings)
                                               (:password settings)
                                               lat lng
                                               last-sync))]
         (dispatch [:set-loading-marks false])
         (if success
           (do
             (dispatch [:sync-marks {:marks (:marks data)
                                      :deleted-ids (:deleted-ids data)
                                      :last-sync (:new-sync data)}])
             (dispatch [:set-is-synced true]))
           (do
             (tel/log! :error {:msg "Failed to fetch marks" :error error})
             (when (has-credentials? settings)
               (notify!
                 dispatch
                 :error
                 (str "Sync failed: " (format-error error)))))))))))

(defn- remote-create-mark!
  [{:keys [settings config]} dispatch mark {:keys [create-mark]}]
  (when (and (remote-allowed? settings) (has-credentials? settings))
    (go
      (let [site (:site config)
            {:keys [success error]} (<! (create-mark
                                         site
                                         (:user settings)
                                         (:password settings)
                                         mark))]
        (if success
          (notify! dispatch :success "Mark added!" (:id mark))
          (do
            (tel/log! :error {:msg "Failed to create mark" :error error})
            (notify!
              dispatch
              :error
              (str "Create failed: " (format-error error))
              (:id mark))))))))

(defn- remote-update-mark!
  [{:keys [settings config]} dispatch mark {:keys [update-mark]}]
  (when (and (remote-allowed? settings) (has-credentials? settings))
    (go
      (let [site (:site config)
            {:keys [success error]} (<! (update-mark
                                         site
                                         (:user settings)
                                         (:password settings)
                                         mark))]
        (if success
          (notify! dispatch :success "Mark updated!" (:id mark))
          (do
            (tel/log! :error {:msg "Failed to update mark" :error error})
            (notify!
              dispatch
              :error
              (str "Update failed: " (format-error error)) (:id mark))))))))

(defn- remote-delete-mark!
  [{:keys [settings config]} dispatch mark-id {:keys [delete-mark]}]
  (when (and (remote-allowed? settings) (has-credentials? settings))
    (go
      (let [site (:site config)
            {:keys [success error]} (<! (delete-mark
                                         site
                                         (:user settings)
                                         (:password settings)
                                         mark-id))]
        (if success
          (notify! dispatch :success "Mark deleted!")
          (do
            (tel/log! :error {:msg "Failed to delete mark" :error error})
            (notify!
              dispatch
              :error
              (str "Delete failed: " (format-error error)))))))))

(defn- remote-vote-mark!
  [{:keys [settings config]} dispatch mark-id value {:keys [vote-mark]}]
  (when (and (remote-allowed? settings) (has-credentials? settings))
    (go
      (let [site (:site config)
            {:keys [success error]} (<! (vote-mark
                                         site
                                         (:user settings)
                                         (:password settings)
                                         mark-id
                                         value))]
        (if success
          (notify! dispatch :success "Vote recorded!" mark-id)
          (do
            (tel/log! :error {:msg "Failed to vote for mark" :error error})
            (notify!
              dispatch
              :error
              (str "Vote failed: " (format-error error))
              mark-id)))))))

(defn upload-all-marks!
  ([app-state dispatch]
   (upload-all-marks! app-state dispatch default-deps))
  ([{:keys [marks settings config]} dispatch {:keys [create-mark]}]
   (let [user (:user settings)
         site (:site config)
         marks-to-upload (filter (fn [s]
                                    (let [creator (:creator s)]
                                      (or (nil? creator)
                                          (empty? (str creator))
                                          (= creator user))))
                                  marks)]
     (cond
       (not (remote-allowed? settings))
       (notify! dispatch :error "Remote operations disabled by settings")

       (not (has-credentials? settings))
       (notify! dispatch :error "Authentication required to upload!")

       (empty? marks-to-upload)
       nil

       :else
       (go
         (notify! dispatch :info (str "Uploading " (count marks-to-upload) " marks..."))
         (let [results (atom [])]
           (doseq [mark marks-to-upload]
             (let [res (<! (create-mark site (:user settings) (:password settings) mark))]
               (swap! results conj res)))
           (let [success-count (count (filter :success @results))
                 fail-count (- (count marks-to-upload) success-count)]
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
                   " marks!"))))))))))

;; Controller Intent Functions

(defn create-mark!
  ([app-state dispatch form-data]
   (create-mark! app-state dispatch form-data default-deps))
  ([app-state dispatch form-data deps]
   (let [creator (get-in app-state [:settings :user])
         site (get-in app-state [:config :site])
         {:keys [success
                 marks
                 error
                 processed-data]} (mark-domain/add-mark
                                    (assoc form-data :site site)
                                    (:marks app-state)
                                    creator)]
     (if success
       (do
         (dispatch [:set-marks marks])
         (dispatch [:set-selected-mark processed-data])
         (dispatch [:set-map-center [(:lat processed-data) (:lon processed-data)]])
         (remote-create-mark! app-state dispatch processed-data deps)
         true)
       (do
         (notify! dispatch :error (format-error error))
         false)))))

(defn update-mark!
  ([app-state dispatch form-data editing-mark]
   (update-mark! app-state dispatch form-data editing-mark default-deps))
  ([app-state dispatch form-data editing-mark deps]
   (let [user (get-in app-state [:settings :user])
         site (get-in app-state [:config :site])
         editing-creator (:creator editing-mark)]
     (if (and (seq (str editing-creator)) (not= user editing-creator))
       (do
         (notify! dispatch :error "Forbidden: You do not own this mark")
         false)
       (let [{:keys [success
                     marks
                     error
                     processed-data]} (mark-domain/edit-mark
                                        (assoc form-data :site site)
                                        (:marks app-state)
                                        editing-mark
                                        user)]
         (if success
           (do
             (dispatch [:set-marks marks])
             (dispatch [:set-selected-mark processed-data])
             (dispatch [:set-map-center [(:lat processed-data) (:lon processed-data)]])
             (remote-update-mark! app-state dispatch processed-data deps)
             true)
           (do
             (notify! dispatch :error (format-error error) (:id editing-mark))
             false)))))))

(defn delete-mark!
  ([app-state dispatch mark]
   (delete-mark! app-state dispatch mark default-deps))
  ([app-state dispatch mark deps]
   (let [user (get-in app-state [:settings :user])
         creator (:creator mark)]
     (if (and (seq (str creator)) (not= user creator))
       (do
         (notify! dispatch :error "Forbidden: You do not own this mark")
         false)
       (do
         (dispatch [:remove-mark mark])
         (remote-delete-mark! app-state dispatch (:id mark) deps)
         true)))))

(defn vote-mark!
  ([app-state dispatch mark value]
   (vote-mark! app-state dispatch mark value default-deps))
  ([app-state dispatch mark value deps]
   (let [old-vote (or (:user-vote mark) 0)
         new-vote (if (= old-vote value) 0 value) ;; Toggle vote if same value
         score-diff (- new-vote old-vote)
         updated-mark (-> mark
                           (assoc :user-vote new-vote)
                           (update :score (fnil + 0) score-diff))]
     (dispatch [:update-mark updated-mark])
     (when (= (:id (:selected-mark app-state)) (:id mark))
       (dispatch [:set-selected-mark updated-mark]))
     (remote-vote-mark! app-state dispatch (:id mark) new-vote deps))))

(defn lookup-address!
  ([app-state dispatch on-update address-data]
   (lookup-address! app-state dispatch on-update address-data default-deps))
  ([app-state dispatch on-update address-data {:keys [geocode-address]}]
   (let [settings (:settings app-state)
         user (:user settings)
         password (:password settings)
         site (get-in app-state [:config :site])
         address (str/join ", " (remove empty? [(:address address-data)
                                                (:town address-data)
                                                (:state address-data)]))]
     (cond
       (not (remote-allowed? settings))
       (notify! dispatch :error "Remote operations disabled by settings")

       (or (empty? user) (empty? password))
       (notify! dispatch :error "Authentication required for address lookup!")

       (empty? address)
       (notify! dispatch :error "Address is empty!")

       :else
       (go
         (notify! dispatch :info "Looking up address...")
         (let [{:keys [success lat lng error]} (<!
                                                 (geocode-address
                                                   site
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
               (str "Geocoding failed: " (format-error error))))))))))

(defn reverse-lookup!
  ([app-state dispatch on-update lat lng]
   (reverse-lookup! app-state dispatch on-update lat lng default-deps))
  ([app-state dispatch on-update lat lng {:keys [reverse-geocode]}]
   (let [settings (:settings app-state)
         user (:user settings)
         password (:password settings)
         site (get-in app-state [:config :site])]
     (cond
       (not (remote-allowed? settings))
       (notify! dispatch :error "Remote operations disabled by settings")

       (or (empty? user) (empty? password))
       (notify! dispatch :error "Authentication required for address lookup!")

       :else
       (go
         (notify! dispatch :info "Determining address...")
         (let [{:keys [success data error]} (<! (reverse-geocode site user password lat lng))]
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
