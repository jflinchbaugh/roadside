(ns com.hjsoft.roadside.website.ui.forms
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.domain.stand :as stand-domain]
            [com.hjsoft.roadside.website.version :as version]
            [com.hjsoft.roadside.website.ui.map :refer [leaflet-map]]
            [com.hjsoft.roadside.website.ui.hooks :as ui-hooks]
            [com.hjsoft.roadside.website.controller :as controller]))

(def add-zoom-level 14)

(defnc form-field
  [{:keys [label type value on-change on-blur rows
           checked id class-name placeholder]
    :or {type "text"}}]
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

(defn- stand-form-reducer [state [action-type payload]]
  (case action-type
    :update-field (assoc state (first payload) (second payload))
    :add-product (if (some #(= % payload) (:products state))
                   state
                   (update state :products #(conj (or % []) payload)))
    :remove-product (update state :products (fn [products]
                                              (filterv #(not= % payload) products)))
    state))

(defnc location-input
  [{:keys
    [stand-form-data
     on-update
     original-coordinate]}]
  (let [{:keys [stands]} (state/use-app-state)
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
  (let [[current-product set-current-product] (hooks/use-state "")
        product-input-ref (hooks/use-ref nil)]
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
         :onChange #(set-current-product (.. % -target -value))
         :onKeyDown (fn [e]
                      (when (= (.-key e) "Enter")
                        (.preventDefault e)
                        (when (not= current-product "")
                          (on-update [:add-product current-product]))
                        (set-current-product "")))
         :enterKeyHint "enter"})
       (d/button
        {:type "button"
         :class "add-product-btn"
         :onClick (fn []
                    (when (not= current-product "")
                      (on-update [:add-product current-product]))
                    (set-current-product "")
                    (when-let [el @product-input-ref] (.focus el)))}
        "Add"))))))

(defnc stand-form []
  (let [app-state (state/use-app-state)
        dispatch (state/use-dispatch)
        {:keys [editing-stand]} (state/use-ui)
        {:keys [create-stand! update-stand! cancel-form!]} (ui-hooks/use-actions)
        [stand-form-data local-dispatch] (hooks/use-reducer
                                          stand-form-reducer
                                          (or editing-stand
                                              (assoc state/default-stand-form-data
                                                     :coordinate (str
                                                                  (first (:map-center app-state))
                                                                  ", "
                                                                  (second (:map-center app-state)))
                                                     :expiration (utils/in-a-week))))
        [show-address? set-show-address?] (hooks/use-state (or (seq (:address stand-form-data))
                                                               (seq (:town stand-form-data))
                                                               (seq (:state stand-form-data))))]

    (ui-hooks/use-escape-key #(cancel-form!))

    (d/div
     {:class "form-overlay"
      :onClick #(cancel-form!)}
     (d/form
      {:class "form-container"
       :onClick #(.stopPropagation %)
       :onSubmit (fn [e]
                   (.preventDefault e)
                   (if editing-stand
                     (update-stand! stand-form-data editing-stand)
                     (create-stand! stand-form-data)))}
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
           :on-change #(local-dispatch [:update-field [:name (.. % -target -value)]])})
       ($ form-field
          {:label "Notes:"
           :type "textarea"
           :value (:notes stand-form-data)
           :on-change #(local-dispatch [:update-field [:notes (.. % -target -value)]])
           :rows 4})
       (d/div
        {:class "form-group"}
        (d/button
         {:type "button"
          :class "toggle-address-btn"
          :onClick #(set-show-address? (not show-address?))}
         (if show-address?
           "Collapse Address \u25B4"
           "Expand Address \u25BE")))
       (when show-address?
         (d/div
          {:class "address-fields-wrapper"}
          ($ form-field
             {:label "Address:"
              :value (:address stand-form-data)
              :on-change #(local-dispatch [:update-field [:address (.. % -target -value)]])})
          ($ form-field
             {:label "Town:"
              :value (:town stand-form-data)
              :on-change #(local-dispatch [:update-field [:town (.. % -target -value)]])})
          ($ form-field
             {:label "State:"
              :value (:state stand-form-data)
              :on-change #(local-dispatch [:update-field [:state (.. % -target -value)]])})))
       ($ form-field
          {:label "Expiration Date:"
           :type "date"
           :value (:expiration stand-form-data)
           :on-change #(local-dispatch [:update-field [:expiration (.. % -target -value)]])})
       ($ form-field
          {:label "Shared?"
           :type "checkbox"
           :id "shared-checkbox"
           :class-name "checkbox"
           :checked (get stand-form-data :shared? false)
           :on-change #(local-dispatch [:update-field [:shared? (.. % -target -checked)]])}))))))

(defnc settings-dialog []
  (let [app-state (state/use-app-state)
        dispatch (state/use-dispatch)
        {:keys [set-show-settings-dialog]} (state/use-ui)
        {:keys [settings]} app-state
        [form-data set-form-data] (hooks/use-state
                                   (or
                                    settings
                                    {:resource "" :user "" :password ""}))]

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
          {:label "Resource:"
           :value (:resource form-data)
           :on-change #(set-form-data (assoc form-data :resource (.. % -target -value)))})
       ($ form-field
          {:label "User:"
           :value (:user form-data)
           :on-change #(set-form-data (assoc form-data :user (.. % -target -value)))})
       ($ form-field
          {:label "Password:"
           :type "password"
           :value (:password form-data)
           :on-change #(set-form-data (assoc form-data :password (.. % -target -value)))})
       (d/div
        {:class "settings-actions"}
        (d/button
         {:type "button"
          :class "button secondary"
          :onClick #(set-show-settings-dialog false)}
         "Cancel")
        (d/button
         {:type "submit"
          :class "button primary"
          :onClick #(do
                      (dispatch [:set-settings form-data])
                      (set-show-settings-dialog false))}
         "Save")))
      (d/div
       {:class "build-date"}
       "Build: " version/build-date)))))
