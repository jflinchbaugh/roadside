(ns com.hjsoft.mapmarks.website.ui.forms.mark
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [clojure.string :as string]
            [com.hjsoft.mapmarks.website.utils :as utils]
            [com.hjsoft.mapmarks.website.state :as state]
            [com.hjsoft.mapmarks.website.domain.mark :as mark-domain]
            [com.hjsoft.mapmarks.website.ui.hooks :as ui-hooks]
            [com.hjsoft.mapmarks.website.ui.forms.field :refer [form-field]]
            [com.hjsoft.mapmarks.website.ui.forms.inputs :refer [location-input tag-input]]
            [com.hjsoft.mapmarks.website.ui.forms.buttons :refer [close-button]]))

(goog-define NODE_TEST false)

(def icon-check "\u2713")
(def icon-expand "\u25BE")
(def icon-collapse "\u25B4")

(defnc mark-form []
  (let [app-state (state/use-app-state)
        settings (:settings app-state)
        config (:config app-state)
        authenticated? (and (seq (:user settings))
                            (seq (:password settings)))
        {:keys [editing-mark]} (state/use-ui)
        {:keys [create-mark!
                update-mark!
                lookup-address!
                reverse-lookup!
                cancel-form!]} (ui-hooks/use-actions)
        [mark-form-data
         local-dispatch] (hooks/use-reducer
                          mark-domain/mark-form-reducer
                          {:editing-mark editing-mark
                           :map-center (:map-center app-state)
                           :default-expiration-days (:default-expiration-days config)}
                          mark-domain/init-form-state)]

    (hooks/use-effect
     [(:map-center app-state)]
     (when-not editing-mark
       (local-dispatch
        [:sync-coordinate (str (first (:map-center app-state))
                               ", "
                               (second (:map-center app-state)))])))

    (ui-hooks/use-escape-key #(cancel-form!))

    (d/div
     {:class "form-overlay"
      :onClick #(cancel-form!)}
     (d/form
      {:class "form-container"
       :onClick #(.stopPropagation %)
       :onSubmit (fn [e]
                   (.preventDefault e)
                   (let [final-data (mark-domain/prepare-submit-data
                                     mark-form-data)]
                     (if editing-mark
                       (update-mark! final-data editing-mark)
                       (create-mark! final-data))))}
      (d/div
       {:class "form-header-actions"}
       (d/h3 (if editing-mark (str "Edit " (:mark-name-singular config)) (str "Add New " (:mark-name-singular config))))
       (d/div {:class "form-header-buttons"}
              ($ close-button {:onClick #(cancel-form!) :title "Cancel"})
              (d/button
               {:type "submit"
                :class "button icon-button primary"
                :title "Save"}
               icon-check)))
      (d/div
       {:class "form-content-wrapper"}
       (when-not NODE_TEST
         ($ location-input
            {:mark-form-data mark-form-data
             :on-update local-dispatch
             :original-coordinate (when editing-mark
                                    (str (:lat editing-mark)
                                         ", "
                                         (:lon editing-mark)))}))
       (when-not (:disable-tags? config)
         ($ tag-input
            {:mark-form-data mark-form-data
             :on-update local-dispatch
             :label (str (:tags-name-plural config) ":")
             :placeholder (str "Add " (:tags-name-article config) " " (string/lower-case (:tags-name-singular config)) " and press Enter")}))
       (when-not (:disable-name? config)
         ($ form-field
            {:label (str (:mark-name-singular config) " Name:")
             :value (:name mark-form-data)
             :on-change #(local-dispatch
                          [:update-field [:name (.. % -target -value)]])}))
       ($ form-field
          {:label "Notes:"
           :type "textarea"
           :value (:notes mark-form-data)
           :on-change #(local-dispatch
                        [:update-field [:notes (.. % -target -value)]])
           :rows 4})
       (d/div
        {:class "form-group"}
        (d/button
         {:type "button"
          :class "toggle-address-btn"
          :onClick #(local-dispatch [:toggle-address])}
         (if (:show-address? mark-form-data)
           (str "Collapse Address " icon-collapse)
           (str "Expand Address " icon-expand))))
       (when (:show-address? mark-form-data)
         (d/div
          {:class "address-fields-wrapper"}
          (when (and authenticated?
                     (utils/parse-coordinates (:coordinate mark-form-data)))
            (let [coords (utils/parse-coordinates (:coordinate mark-form-data))]
              (d/button
               {:type "button"
                :class "button secondary reverse-lookup-btn"
                :onClick #(reverse-lookup! local-dispatch
                                           (first coords)
                                           (second coords))
                :title "Lookup address from map coordinates"}
               "From Map")))
          ($ form-field
             {:label "Address:"
              :value (:address mark-form-data)
              :on-change #(local-dispatch
                           [:update-field [:address (.. % -target -value)]])})
          ($ form-field
             {:label "Town:"
              :value (:town mark-form-data)
              :on-change #(local-dispatch
                           [:update-field [:town (.. % -target -value)]])})
          ($ form-field
             {:label "State:"
              :value (:state mark-form-data)
              :on-change #(local-dispatch
                           [:update-field [:state (.. % -target -value)]])})
          (when authenticated?
            (d/button
             {:type "button"
              :class "button secondary lookup-btn"
              :onClick #(lookup-address! local-dispatch mark-form-data)}
             "To Map"))))
       (when (pos? (or (:default-expiration-days config) 0))
         ($ form-field
            {:label "Expiration Date:"
             :type "date"
             :value (:expiration mark-form-data)
             :on-change #(local-dispatch
                          [:update-field [:expiration (.. % -target -value)]])}))
       ($ form-field
          {:label "Shared?"
           :type "checkbox"
           :id "shared-checkbox"
           :class-name "checkbox"
           :checked (get mark-form-data :shared? false)
           :on-change #(local-dispatch
                        [:update-field
                         [:shared? (.. % -target -checked)]])}))))))
