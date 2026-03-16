(ns com.hjsoft.roadside.website.ui.forms.stand
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.domain.stand :as stand-domain]
            [com.hjsoft.roadside.website.ui.hooks :as ui-hooks]
            [com.hjsoft.roadside.website.ui.forms.field :refer [form-field]]
            [com.hjsoft.roadside.website.ui.forms.inputs :refer [location-input product-input]]))

(goog-define NODE_TEST false)

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
       (when-not NODE_TEST
         ($ location-input
            {:stand-form-data stand-form-data
             :on-update local-dispatch
             :original-coordinate (:coordinate editing-stand)}))
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
