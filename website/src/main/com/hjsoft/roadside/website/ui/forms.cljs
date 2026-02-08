(ns com.hjsoft.roadside.website.ui.forms
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.version :as version]
            [com.hjsoft.roadside.website.ui.map :refer [leaflet-map]]
            [clojure.string :as str]))

(defnc form-field
  [{:keys [label type value on-change on-blur rows
           checked id class-name input-ref placeholder]
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
       :ref input-ref
       :rows (or rows 3)
       :placeholder placeholder})
     (d/input {:id id
               :value value
               :checked checked
               :type type
               :onChange on-change
               :onBlur on-blur
               :ref input-ref
               :class class-name
               :placeholder placeholder}))))

(defn- add-product-to-form-data
  [current-product dispatch]
  (when (not= current-product "")
    (dispatch
     [:set-stand-form-data
      (fn [prev]
        (if (some #(= % current-product) (:products prev))
          prev
          (assoc
           prev
           :products (conj (:products prev) current-product))))])))

(defnc location-input
  [{:keys
    [coordinate-input-ref
     location-btn-ref
     add-zoom-level]}]
  (let [{:keys [dispatch state user-location]} (hooks/use-context state/app-context)
        {:keys [stand-form-data stands]} state
        {:keys [location error is-locating get-location cancel-location]} user-location
        [coordinate-display set-coordinate-display] (hooks/use-state
                                                     (:coordinate stand-form-data))
        map-ref (hooks/use-ref nil)]
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
         :set-coordinate-form-data (fn [coord-str]
                                     (dispatch [:set-stand-form-data
                                                (fn [prev]
                                                  (assoc prev
                                                    :coordinate coord-str))]))
         :map-ref map-ref
         :is-locating is-locating
         :on-cancel-location cancel-location
         :current-location-coords location})
     (d/label "Coordinate:")
     (d/div
      {:class "coordinate-input-group"}
      (d/input
       {:type "text"
        :ref coordinate-input-ref
        :value coordinate-display
        :onChange #(set-coordinate-display (.. % -target -value))
        :onBlur #(dispatch [:set-stand-form-data
                            (fn [prev]
                              (assoc prev
                                :coordinate coordinate-display))])
        :class "coordinate-input"})
      (d/button
       {:type "button"
        :class "location-btn"
        :ref location-btn-ref
        :onClick (fn []
                   (get-location
                    (fn [[lat lng]]
                      (dispatch [:set-stand-form-data
                                 (fn [prev]
                                   (assoc prev
                                     :coordinate (str lat ", " lng)))]))))}
       "\u2316"))
     (when error
       (d/p
        {:class "error-message"}
        error)))))

(defnc product-input []
  (let [{:keys [dispatch state]} (hooks/use-context state/app-context)
        {:keys [stand-form-data]} state
        [current-product set-current-product] (hooks/use-state "")]
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
                 :onClick #(dispatch
                            [:set-stand-form-data
                             (fn [prev]
                               (assoc
                                prev
                                :products (->> prev
                                               :products
                                               (remove #{product})
                                               vec)))])}
                "\u2715")))
            (filter string? (:products stand-form-data))))
      (d/div
       {:class "product-input-group"}
       (d/input
        {:type "text"
         :value current-product
         :placeholder "Add a product and press Enter"
         :onChange #(set-current-product (.. % -target -value))
         :onKeyDown (fn [e]
                      (when (= (.-key e) "Enter")
                        (.preventDefault e)
                        (add-product-to-form-data current-product dispatch)
                        (set-current-product "")))
         :enterKeyHint "enter"})
       (d/button
        {:type "button"
         :class "add-product-btn"
         :onClick (fn []
                    (add-product-to-form-data current-product dispatch)
                    (set-current-product ""))}
        "Add"))))))

(defnc stand-form
  [{:keys [add-zoom-level]}]
  (let [{:keys [dispatch state user-location]} (hooks/use-context state/app-context)
        {:keys [stand-form-data editing-stand show-form stands]} state
        [show-address? set-show-address?] (hooks/use-state false)
        coordinate-input-ref (hooks/use-ref nil)
        location-btn-ref (hooks/use-ref nil)]
    (hooks/use-effect
     [(:address stand-form-data) (:town stand-form-data) (:state stand-form-data)]
     (when (or (seq (:address stand-form-data))
               (seq (:town stand-form-data))
               (seq (:state stand-form-data)))
       (set-show-address? true)))

    (hooks/use-effect
     [show-form]
     (when show-form
       (let [handle-keydown (fn [e]
                              (when (= (.-key e) "Escape")
                                (dispatch [:close-form])))]
         (.addEventListener js/document "keydown" handle-keydown)
         (.focus @coordinate-input-ref)
         (fn []
           (.removeEventListener js/document "keydown" handle-keydown)))))

    (when show-form
      (d/div
       {:class "form-overlay"
        :onClick #(dispatch [:close-form])}
       (d/form
        {:class "form-container"
         :onClick #(.stopPropagation %)
         :onSubmit (fn [e]
                     (.preventDefault e)
                     (let [{:keys [success stands error]} (state/process-stand-form
                                                           stand-form-data
                                                           (:stands state)
                                                           editing-stand)]
                       (if success
                         (do
                           (dispatch [:set-stands stands])
                           (dispatch [:close-form]))
                         (dispatch [:set-notification {:type :error :message error}]))))}
        (d/div
         {:class "form-header-actions"}
         (d/h3 (if editing-stand "Edit Stand" "Add New Stand"))
         (d/div {:class "form-header-buttons"}
                (d/button
                 {:type "button"
                  :class "button icon-button"
                  :onClick #(dispatch [:close-form])
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
            {:coordinate-input-ref coordinate-input-ref
             :user-location user-location
             :location-btn-ref location-btn-ref
             :add-zoom-level add-zoom-level})
         ($ product-input)
         ($ form-field
            {:label "Stand Name:"
             :value (:name stand-form-data)
             :on-change #(dispatch [:set-stand-form-data
                                    (fn [prev]
                                      (assoc prev
                                        :name (.. % -target -value)))])})
         ($ form-field
            {:label "Notes:"
             :type "textarea"
             :value (:notes stand-form-data)
             :on-change #(dispatch [:set-stand-form-data
                                    (fn [prev]
                                      (assoc prev
                                        :notes (.. % -target -value)))])
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
                :on-change #(dispatch [:set-stand-form-data
                                       (fn [prev]
                                         (assoc prev
                                           :address (.. % -target -value)))])})
            ($ form-field
               {:label "Town:"
                :value (:town stand-form-data)
                :on-change #(dispatch [:set-stand-form-data
                                       (fn [prev]
                                         (assoc prev
                                           :town (.. % -target -value)))])})
            ($ form-field
               {:label "State:"
                :value (:state stand-form-data)
                :on-change #(dispatch [:set-stand-form-data
                                       (fn [prev]
                                         (assoc prev
                                           :state (.. % -target -value)))])})))
         ($ form-field
            {:label "Expiration Date:"
             :type "date"
             :value (:expiration stand-form-data)
             :on-change #(dispatch [:set-stand-form-data
                                    (fn [prev]
                                      (assoc prev
                                        :expiration (.. % -target -value)))])})
         ($ form-field
            {:label "Shared?"
             :type "checkbox"
             :id "shared-checkbox"
             :class-name "checkbox"
             :checked (get stand-form-data :shared? false)
             :on-change #(dispatch [:set-stand-form-data
                                    (fn [prev]
                                      (assoc prev
                                        :shared? (get (.. % -target)
                                                   "checked")))])})))))))

(defnc settings-dialog []
  (let [{:keys [dispatch state]} (hooks/use-context state/app-context)
        {:keys [show-settings-dialog settings-form-data]} state]
    (when show-settings-dialog
      (d/div
       {:class "settings-overlay"
        :onClick #(dispatch [:set-show-settings-dialog false])}
       (d/div
        {:class "settings-dialog"
         :onClick #(.stopPropagation %)}
        (d/div
         {:class "settings-header"}
         (d/h3 "Settings")
         (d/button
          {:class "button icon-button"
           :onClick #(dispatch [:set-show-settings-dialog false])
           :title "Close"}
          "\u2715"))
        (d/div
         {:class "settings-content"}
         ($ form-field
            {:label "Resource:"
             :value (:resource settings-form-data)
             :on-change #(dispatch [:set-settings-form-data
                                    (fn [prev]
                                      (assoc prev
                                        :resource (.. % -target -value)))])})
         ($ form-field
            {:label "User:"
             :value (:user settings-form-data)
             :on-change #(dispatch [:set-settings-form-data
                                    (fn [prev]
                                      (assoc prev
                                        :user (.. % -target -value)))])})
         ($ form-field
            {:label "Password:"
             :type "password"
             :value (:password settings-form-data)
             :on-change #(dispatch [:set-settings-form-data
                                    (fn [prev]
                                      (assoc prev
                                        :password (.. % -target -value)))])})
         (d/div
          {:class "settings-actions"}
          (d/button
           {:type "button"
            :class "button secondary"
            :onClick #(dispatch [:set-show-settings-dialog false])}
           "Cancel")
          (d/button
           {:type "submit"
            :class "button primary"
            :onClick #(do
                        (dispatch [:set-settings settings-form-data])
                        (dispatch [:set-show-settings-dialog false]))}
           "Save")))
        (d/div
         {:style {:padding "1rem"
                  :font-size "0.75rem"
                  :color "#888"
                  :text-align "center"
                  :border-top "1px solid #eee"}}
         "Build: " version/build-date))))))
