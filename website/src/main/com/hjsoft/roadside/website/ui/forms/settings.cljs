(ns com.hjsoft.roadside.website.ui.forms.settings
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.version :as version]
            [com.hjsoft.roadside.website.ui.hooks :as ui-hooks]
            [com.hjsoft.roadside.website.api :as api]
            [com.hjsoft.roadside.website.ui.forms.field :refer [form-field]]
            [cljs.core.async :refer [go <!]]
            [clojure.string :as str]))

(defnc settings-dialog [{:keys [register-fn]}]
  (let [app-state (state/use-app-state)
        dispatch (state/use-dispatch)
        {:keys [set-show-settings-dialog]} (state/use-ui)
        {:keys [settings]} app-state
        [registering? set-registering] (hooks/use-state false)
        [register-error set-register-error] (hooks/use-state nil)
        form-data (merge {:user "" :password "" :email "" :local-only? false} settings)
        [form-data set-form-data] (hooks/use-state form-data)
        handle-register (fn []
                          (if (:local-only? form-data)
                            (set-register-error ["Registration disabled in Local Only mode"])
                            (go
                              (let [register (or register-fn api/register-user)
                                    res (<! (register
                                             (:user form-data)
                                             (:password form-data)
                                             (:email form-data)))]
                                (if (:success res)
                                  (do
                                    (dispatch [:set-notification
                                               {:type :success
                                                :message "Registered successfully!"}])
                                    (set-register-error nil)
                                    (dispatch [:set-settings (dissoc form-data :email)])
                                    (set-show-settings-dialog false))
                                  (set-register-error (:error res)))))))]

    (ui-hooks/use-escape-key #(set-show-settings-dialog false))

    (d/div
     {:class "settings-overlay"
      :onClick #(set-show-settings-dialog false)}
     (d/div
      {:class "settings-dialog"
       :onClick #(.stopPropagation %)}
      (d/div
       {:class "settings-header"}
       (d/h3 "Settings")
       (d/button
        {:class "button icon-button"
         :onClick #(set-show-settings-dialog false)
         :title "Close"}
        "\u2715"))
      (d/div
       {:class "settings-content"}
       (when (and registering? (seq register-error))
         (d/div
          {:class "error-message"}
          (d/ul
           (for [err register-error]
             (d/li {:key err} err)))))
       ($ form-field
          {:label "User:"
           :id "settings-user"
           :value (:user form-data)
           :on-change #(do
                         (set-register-error nil)
                         (set-form-data
                          (assoc form-data :user (.. % -target -value))))})
       ($ form-field
          {:label "Password:"
           :id "settings-password"
           :type "password"
           :value (:password form-data)
           :on-change #(do
                         (set-register-error nil)
                         (set-form-data
                          (assoc form-data :password (.. % -target -value))))})
       (when registering?
         ($ form-field
            {:label "Email:"
             :id "settings-email"
             :value (:email form-data)
             :on-change #(do
                           (set-register-error nil)
                           (set-form-data
                            (assoc form-data :email (.. % -target -value))))}))
       ($ form-field
          {:label "Local Only:"
           :id "settings-local-only"
           :type "checkbox"
           :checked (:local-only? form-data)
           :on-change #(set-form-data
                        (assoc form-data :local-only? (.. % -target -checked)))})
       (d/div
        {:class "register-toggle"}
        (if registering?
          (d/a {:href "#"
                :onClick #(do
                            (set-registering false)
                            (set-register-error nil))}
               "Already have an account? Sign in")
          (d/a {:href "#"
                :onClick #(do
                            (set-registering true)
                            (set-register-error nil))}
               "Don't have an account? Register")))
       (d/div
        {:class "settings-actions"}
        (d/button
         {:type "button"
          :class "button secondary"
          :onClick #(set-show-settings-dialog false)}
         "Cancel")
        (if registering?
          (d/button
           {:type "button"
            :class "button primary"
            :onClick handle-register}
           "Register")
          (d/button
           {:type "submit"
            :class "button primary"
            :onClick #(do
                        (dispatch [:set-settings (dissoc form-data :email)])
                        (set-show-settings-dialog false))}
           "Save"))))
      (d/div
       {:class "build-date"}
       "Build: " version/build-date
       (d/br)
       (d/a {:href "mailto:john@hjsoft.com"} "john@hjsoft.com"))))))
