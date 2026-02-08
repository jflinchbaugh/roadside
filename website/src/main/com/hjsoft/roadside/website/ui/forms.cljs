(ns com.hjsoft.roadside.website.ui.forms
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.ui.map :refer [leaflet-map]]
            [clojure.string :as str]))

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
     user-location
     location-btn-ref
     add-zoom-level]}]
  (let [{:keys [dispatch state]} (hooks/use-context state/app-context)
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
         :center (or (utils/parse-coordinates (:coordinate stand-form-data)) state/map-home)
         :zoom-level add-zoom-level
         :stands stands
         :show-crosshairs true
         :set-coordinate-form-data (fn [coord-str]
                                     (dispatch [:set-stand-form-data
                                                (fn [prev] (assoc prev :coordinate coord-str))]))
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
                            (fn [prev] (assoc prev :coordinate coordinate-display))])
        :class "coordinate-input"})
      (d/button
       {:type "button"
        :class "location-btn"
        :ref location-btn-ref
        :onClick (fn []
                   (get-location
                    (fn [[lat lng]]
                      (dispatch [:set-stand-form-data
                                 (fn [prev] (assoc prev :coordinate (str lat ", " lng)))]))))}
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
            (:products stand-form-data)))
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
         :enterkeyhint "enter"})
       (d/button
        {:type "button"
         :class "add-product-btn"
         :onClick (fn []
                    (add-product-to-form-data current-product dispatch)
                    (set-current-product ""))}
        "Add"))))))

(defnc stand-form
  [{:keys [user-location
           add-zoom-level]}]
  (let [{:keys [dispatch state]} (hooks/use-context state/app-context)
        {:keys [stand-form-data editing-stand show-form stands map-center]} state
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
     (if-not show-form
       (do
         (dispatch [:set-editing-stand nil])
         (set-show-address? false)
         (dispatch [:set-stand-form-data
                    {:name ""
                     :coordinate (str
                                  (first state/map-home)
                                  ", "
                                  (second state/map-home))
                     :address ""
                     :town ""
                     :state ""
                     :products []
                     :expiration (utils/in-a-week)
                     :notes ""
                     :shared? true}]))
       (do
         (.addEventListener
          js/document
          "keydown"
          (fn [e]
            (when (= (.-key e) "Escape")
              (dispatch [:set-show-form false]))))
         (.focus @coordinate-input-ref)
         (when (nil? editing-stand)
           (dispatch [:set-stand-form-data
                      (fn [prev]
                        (assoc
                         prev
                         :coordinate (str
                                      (first (or map-center state/map-home))
                                      ", "
                                      (second (or map-center state/map-home)))))])))))

    (when show-form
      (d/div
       {:class "form-overlay"
        :onClick #(dispatch [:set-show-form false])}
       (d/form
        {:class "form-container"
         :onClick #(.stopPropagation %)
         :onSubmit (fn [e]
                     (.preventDefault e)
                     (let [new-stands (state/process-stand-form
                                       stand-form-data
                                       stands
                                       editing-stand)]
                       (when (not= new-stands stands)
                         (dispatch [:set-stands new-stands])
                         (dispatch [:set-show-form false]))))}
        (d/div
         {:class "form-header-actions"}
         (d/h3 (if editing-stand "Edit Stand" "Add New Stand"))
         (d/div {:class "form-header-buttons"}
                (d/button
                 {:type "button"
                  :class "button icon-button"
                  :on-click #(dispatch [:set-show-form false])
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
         (d/div
          {:class "form-group"}
          (d/label "Stand Name:")
          (d/input
           {:type "text"
            :value (:name stand-form-data)
            :onChange #(dispatch [:set-stand-form-data
                                  (fn [prev] (assoc prev :name (.. % -target -value)))])}))
         (d/div
          {:class "form-group"}
          (d/label "Notes:")
          (d/textarea
           {:value (:notes stand-form-data)
            :onChange #(dispatch [:set-stand-form-data
                                  (fn [prev] (assoc prev :notes (.. % -target -value)))])
            :rows 4}))
         (d/div
          {:class "form-group"}
          (d/button
           {:type "button"
            :class "toggle-address-btn"
            :onClick #(set-show-address? not)}
           (if show-address?
             "Collapse Address \u25B4"
             "Expand Address \u25BE")))
         (when show-address?
           (d/div
            {:class "address-fields-wrapper"}
            (d/div
             {:class "form-group"}
             (d/label "Address:")
             (d/input
              {:type "text"
               :value (:address stand-form-data)
               :onChange #(dispatch [:set-stand-form-data
                                     (fn [prev] (assoc prev :address (.. % -target -value)))])}))
            (d/div
             {:class "form-group"}
             (d/label "Town:")
             (d/input
              {:type "text"
               :value (:town stand-form-data)
               :onChange #(dispatch [:set-stand-form-data
                                     (fn [prev] (assoc prev :town (.. % -target -value)))])}))
            (d/div
             {:class "form-group"}
             (d/label "State:")
             (d/input
              {:type "text"
               :value (:state stand-form-data)
               :onChange #(dispatch [:set-stand-form-data
                                     (fn [prev] (assoc prev :state (.. % -target -value)))])}))))
         (d/div
          {:class "form-group"}
          (d/label "Expiration Date:")
          (d/input
           {:type "date"
            :value (:expiration stand-form-data)
            :onChange #(dispatch [:set-stand-form-data
                                  (fn [prev] (assoc prev :expiration (.. % -target -value)))])}))
         (d/div
          {:class "form-group"}
          (d/label {:for "shared-checkbox"} "Shared?")
          (d/input
           {:id "shared-checkbox"
            :class "checkbox"
            :type "checkbox"
            :checked (get stand-form-data :shared? false)
            :onChange #(dispatch [:set-stand-form-data
                                  (fn [prev] (assoc prev :shared? (.. % -target -checked)))])}))))))))

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
         (d/div
          {:class "form-group"}
          (d/label "Resource:")
          (d/input
           {:type "text"
            :value (:resource settings-form-data)
            :onChange #(dispatch [:set-settings-form-data
                                  (fn [prev] (assoc prev :resource (.. % -target -value)))])}))
         (d/div
          {:class "form-group"}
          (d/label "User:")
          (d/input
           {:type "text"
            :value (:user settings-form-data)
            :onChange #(dispatch [:set-settings-form-data
                                  (fn [prev] (assoc prev :user (.. % -target -value)))])}))
         (d/div
          {:class "form-group"}
          (d/label "Password:")
          (d/input
           {:type "password"
            :value (:password settings-form-data)
            :onChange #(dispatch [:set-settings-form-data
                                  (fn [prev] (assoc prev :password (.. % -target -value)))])}))
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
           "Save"))))))))
