(ns com.hjsoft.roadside.website.ui.forms
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.domain.stand :as stand-domain]
            [com.hjsoft.roadside.website.version :as version]
            [com.hjsoft.roadside.website.ui.map :refer [leaflet-map] ]
            [com.hjsoft.roadside.website.ui.hooks :as ui-hooks]
            [com.hjsoft.roadside.website.api :as api]
            [cljs.core.async :refer [go <!]]
            [clojure.string :as str]))

(def add-zoom-level 14)

(defnc form-field
  [{:keys [label type value on-change on-blur rows
           checked id class-name placeholder]
    :or {type "text"
         value ""}}]
  (d/div
   {:class "form-group"}
   (when label
     (d/label {:for id} label))
   (if (= type "textarea")
     (d/textarea
      {:value value
       :onChange on-change
       :onBlur on-blur
       :rows (or rows 3)
       :placeholder placeholder})
     (d/input {:id id
               :value value
               :checked checked
               :type type
               :onChange on-change
               :onBlur on-blur
               :class class-name
               :placeholder placeholder}))))

(defnc location-input
  [{:keys
    [stand-form-data
     on-update
     original-coordinate]}]
  (let [app-state (state/use-app-state)
        stands (state/select-stands-by-expiry app-state)
        {:keys [get-location error]} (state/use-user-location-state)
        [coordinate-display set-coordinate-display] (hooks/use-state
                                                     (:coordinate stand-form-data))
        coordinate-input-ref (hooks/use-ref nil)]

    (hooks/use-effect
     :once
     (when-let [el @coordinate-input-ref]
       (.focus el)))

    (hooks/use-effect
     [(:coordinate stand-form-data)]
     (set-coordinate-display (:coordinate stand-form-data)))

    (d/div
     {:class "form-group"}
     ($ leaflet-map
        {:div-id "map-form"
         :center (or
                  (utils/parse-coordinates (:coordinate stand-form-data))
                  state/map-home)
         :zoom-level add-zoom-level
         :stands stands
         :show-crosshairs true
         :auto-pan? false
         :set-coordinate-form-data (fn [coord-str]
                                     (on-update [:update-field [:coordinate coord-str]]))})
     (d/label "Coordinate:")
     (d/div
      {:class "coordinate-input-group"}
      (d/input
       {:type "text"
        :ref coordinate-input-ref
        :value coordinate-display
        :onChange #(set-coordinate-display (.. % -target -value))
        :onBlur #(on-update [:update-field [:coordinate coordinate-display]])
        :class "coordinate-input"})
      (when original-coordinate
        (d/button
         {:type "button"
          :class "reset-location-btn"
          :onClick #(on-update [:update-field [:coordinate original-coordinate]])
          :title "Reset to original location"}
         "\u21BA"))
      (d/button
       {:type "button"
        :class "location-btn"
        :onClick (fn []
                   (get-location
                    (fn [[lat lng]]
                      (on-update [:update-field [:coordinate (str lat ", " lng)]]))))}
       "\u2316"))
     (when error
       (d/p
        {:class "error-message"}
        error)))))

(defnc product-input [{:keys [stand-form-data on-update]}]
  (let [product-input-ref (hooks/use-ref nil)
        current-product (:current-product stand-form-data "")]
    (d/div
     {:class "product-section-wrapper"}
     (d/div
      {:class "form-group"}
      (d/label "Products:")
      (d/div
       {:class "products-tags"}
       (map (fn [product]
              (d/span
               {:key product
                :class "product-tag"}
               product
               (d/button
                {:type "button"
                 :class "remove-tag"
                 :onClick #(on-update [:remove-product product])}
                "\u2715")))
            (filter string? (:products stand-form-data))))
      (d/div
       {:class "product-input-group"}
       (d/input
        {:type "text"
         :ref product-input-ref
         :value current-product
         :placeholder "Add a product and press Enter"
         :onChange #(on-update [:update-current-product (.. % -target -value)])
         :onKeyDown (fn [e]
                      (when (= (.-key e) "Enter")
                        (.preventDefault e)
                        (on-update [:add-product])))
         :enterKeyHint "enter"})
       (d/button
        {:type "button"
         :class "add-product-btn"
         :onClick (fn []
                    (on-update [:add-product])
                    (when-let [el @product-input-ref] (.focus el)))}
        "Add"))))))

(defnc stand-form []
  (let [app-state (state/use-app-state)
        settings (:settings app-state)
        authenticated? (and (seq (:user settings))
                            (seq (:password settings)))
        {:keys [editing-stand]} (state/use-ui)
        {:keys [create-stand!
                update-stand!
                lookup-address!
                reverse-lookup!
                cancel-form!]} (ui-hooks/use-actions)
        [stand-form-data
         local-dispatch] (hooks/use-reducer
                          stand-domain/stand-form-reducer
                          {:editing-stand editing-stand
                           :map-center (:map-center app-state)}
                          stand-domain/init-form-state)]

    (ui-hooks/use-escape-key #(cancel-form!))

    (d/div
     {:class "form-overlay"
      :onClick #(cancel-form!)}
     (d/form
      {:class "form-container"
       :onClick #(.stopPropagation %)
       :onSubmit (fn [e]
                   (.preventDefault e)
                   (let [final-data (stand-domain/prepare-submit-data
                                      stand-form-data)]
                     (if editing-stand
                       (update-stand! final-data editing-stand)
                       (create-stand! final-data))))}
      (d/div
       {:class "form-header-actions"}
       (d/h3 (if editing-stand "Edit Stand" "Add New Stand"))
       (d/div {:class "form-header-buttons"}
              (d/button
               {:type "button"
                :class "button icon-button"
                :onClick #(cancel-form!)
                :title "Cancel"}
               "\u2715")
              (d/button
               {:type "submit"
                :class "button icon-button primary"
                :title "Save"}
               "\u2713")))
      (d/div
       {:class "form-content-wrapper"}
       ($ location-input
          {:stand-form-data stand-form-data
           :on-update local-dispatch
           :original-coordinate (:coordinate editing-stand)})
       ($ product-input
          {:stand-form-data stand-form-data
           :on-update local-dispatch})
       ($ form-field
          {:label "Stand Name:"
           :value (:name stand-form-data)
           :on-change #(local-dispatch
                        [:update-field [:name (.. % -target -value)]])})
       ($ form-field
          {:label "Notes:"
           :type "textarea"
           :value (:notes stand-form-data)
           :on-change #(local-dispatch
                        [:update-field [:notes (.. % -target -value)]])
           :rows 4})
       (d/div
        {:class "form-group"}
        (d/button
         {:type "button"
          :class "toggle-address-btn"
          :onClick #(local-dispatch [:toggle-address])}
         (if (:show-address? stand-form-data)
           "Collapse Address \u25B4"
           "Expand Address \u25BE")))
       (when (:show-address? stand-form-data)
         (d/div
          {:class "address-fields-wrapper"}
          (when (and authenticated?
                     (utils/parse-coordinates (:coordinate stand-form-data)))
            (let [coords (utils/parse-coordinates (:coordinate stand-form-data))]
              (d/button
               {:type "button"
                :class "button secondary reverse-lookup-btn"
                :onClick #(reverse-lookup! local-dispatch
                                           (first coords)
                                           (second coords))
                :title "Lookup address from map coordinates"}
               "Find Address")))
          ($ form-field
             {:label "Address:"
              :value (:address stand-form-data)
              :on-change #(local-dispatch
                           [:update-field [:address (.. % -target -value)]])})
          ($ form-field
             {:label "Town:"
              :value (:town stand-form-data)
              :on-change #(local-dispatch
                           [:update-field [:town (.. % -target -value)]])})
          ($ form-field
             {:label "State:"
              :value (:state stand-form-data)
              :on-change #(local-dispatch
                           [:update-field [:state (.. % -target -value)]])})
          (when authenticated?
            (d/button
             {:type "button"
              :class "button secondary lookup-btn"
              :onClick #(lookup-address! local-dispatch stand-form-data)}
             "Show Location"))))
       ($ form-field
          {:label "Expiration Date:"
           :type "date"
           :value (:expiration stand-form-data)
           :on-change #(local-dispatch
                        [:update-field [:expiration (.. % -target -value)]])})
       ($ form-field
          {:label "Shared?"
           :type "checkbox"
           :id "shared-checkbox"
           :class-name "checkbox"
           :checked (get stand-form-data :shared? false)
           :on-change #(local-dispatch
                        [:update-field
                         [:shared? (.. % -target -checked)]])}))))))

(defnc settings-dialog []
  (let [app-state (state/use-app-state)
        dispatch (state/use-dispatch)
        {:keys [set-show-settings-dialog]} (state/use-ui)
        {:keys [settings]} app-state
        [registering? set-registering] (hooks/use-state false)
        form-data (merge {:user "" :password "" :email ""} settings)
        [form-data set-form-data] (hooks/use-state form-data)
        can-register? (and (not (str/blank? (:user form-data)))
                           (not (str/blank? (:password form-data)))
                           (not (str/blank? (:email form-data))))
        can-save? (and (not (str/blank? (:user form-data)))
                       (not (str/blank? (:password form-data))))
        handle-register (fn []
                          (when can-register?
                            (go
                              (let [res (<! (api/register-user
                                             (:user form-data)
                                             (:password form-data)
                                             (:email form-data)))]
                                (if (:success res)
                                  (do
                                    (dispatch [:notify {:type :success :message "Registered successfully!"}])
                                    (dispatch [:set-settings (dissoc form-data :email)])
                                    (set-show-settings-dialog false))
                                  (dispatch [:notify {:type :error :message (str "Registration failed: " (:error res))}]))))))]

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
       ($ form-field
          {:label "User:"
           :value (:user form-data)
           :on-change #(set-form-data
                        (assoc form-data :user (.. % -target -value)))})
       ($ form-field
          {:label "Password:"
           :type "password"
           :value (:password form-data)
           :on-change #(set-form-data
                        (assoc form-data :password (.. % -target -value)))})
       (when registering?
         ($ form-field
            {:label "Email:"
             :value (:email form-data)
             :on-change #(set-form-data
                          (assoc form-data :email (.. % -target -value)))}))
       (d/div
        {:class "register-toggle"}
        (if registering?
          (d/a {:href "#" :onClick #(set-registering false)} "Already have an account? Sign in")
          (d/a {:href "#" :onClick #(set-registering true)} "Don't have an account? Register")))
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
            :disabled (not can-register?)
            :onClick handle-register}
           "Register")
          (d/button
           {:type "submit"
            :class "button primary"
            :disabled (not can-save?)
            :onClick #(do
                        (dispatch [:set-settings (dissoc form-data :email)])
                        (set-show-settings-dialog false))}
           "Save"))))
      (d/div
       {:class "build-date"}
       "Build: " version/build-date
       (d/br)
       (d/a {:href "mailto:john@hjsoft.com"} "john@hjsoft.com"))))))
