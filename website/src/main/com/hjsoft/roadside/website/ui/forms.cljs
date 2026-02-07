(ns com.hjsoft.roadside.website.ui.forms
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.ui.map :refer [leaflet-map]]
            [clojure.string :as str]))

(defn- add-product-to-form-data
  [current-product set-form-data]
  (when (not= current-product "")
    (set-form-data
     (fn [prev]
       (if (some #(= % current-product) (:products prev))
         prev
         (assoc
          prev
          :products (conj (:products prev) current-product)))))))

(defnc location-input
  [{:keys
    [coordinate-input-ref
     user-location
     form-data
     set-form-data
     location-btn-ref
     stands
     map-home
     add-zoom-level]}]
  (let [{:keys [location error is-locating get-location cancel-location]} user-location
        [coordinate-display set-coordinate-display] (hooks/use-state
                                                     (:coordinate form-data))
        map-ref (hooks/use-ref nil)]
    (hooks/use-effect
     [(:coordinate form-data)]
     (set-coordinate-display (:coordinate form-data)))

    (d/div
     {:class "form-group"}
     ($ leaflet-map
        {:div-id "map-form"
         :center (or (utils/parse-coordinates (:coordinate form-data)) map-home)
         :zoom-level add-zoom-level
         :stands stands
         :show-crosshairs true
         :set-coordinate-form-data (fn [coord-str]
                                     (set-form-data
                                      (fn [prev]
                                        (assoc prev :coordinate coord-str))))
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
        :onBlur #(set-form-data
                  (fn [prev]
                    (assoc
                     prev
                     :coordinate coordinate-display)))
        :class "coordinate-input"})
      (d/button
       {:type "button"
        :class "location-btn"
        :ref location-btn-ref
        :onClick (fn []
                   (get-location
                    (fn [[lat lng]]
                      (set-form-data
                       (fn [prev]
                         (assoc
                          prev
                          :coordinate (str lat ", " lng)))))))}
       "\u2316"))
     (when error
       (d/p
        {:class "error-message"}
        error)))))

(defnc product-input
  [{:keys [form-data
           set-form-data]}]
  (let [[current-product set-current-product] (hooks/use-state "")]
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
                 :onClick #(set-form-data
                            (fn [prev]
                              (assoc
                               prev
                               :products (->> prev
                                              :products
                                              (remove #{product})
                                              vec))))}
                "\u2715")))
            (:products form-data)))
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
                        (add-product-to-form-data
                         current-product
                         set-form-data)
                        (set-current-product "")))
         :enterkeyhint "enter"})
       (d/button
        {:type "button"
         :class "add-product-btn"
         :onClick (fn []
                    (add-product-to-form-data
                     current-product
                     set-form-data)
                    (set-current-product ""))}
        "Add"))))))

(defn- update-stand
  [form-data editing-stand current-stands]
  (vec
   (map
    #(if (= % editing-stand)
       (assoc form-data :updated (utils/get-current-timestamp))
       %)
    current-stands)))

(defn- add-stand
  [form-data current-stands]
  (if (some #(= (utils/stand-key form-data) (utils/stand-key %)) current-stands)
    (do
      (js/alert "This stand already exists!")
      current-stands)
    (conj current-stands (assoc form-data :updated (utils/get-current-timestamp)))))

(defnc stand-form
  [{:keys [form-data
           set-form-data
           editing-stand
           set-editing-stand
           show-form
           set-show-form
           user-location
           user-map-center
           stands
           set-stands
           map-home
           add-zoom-level]}]
  (let [[current-product set-current-product] (hooks/use-state "")
        [show-address? set-show-address?] (hooks/use-state false)
        coordinate-input-ref (hooks/use-ref nil)
        location-btn-ref (hooks/use-ref nil)]
    (hooks/use-effect
     [(:address form-data) (:town form-data) (:state form-data)]
     (when (or (seq (:address form-data))
               (seq (:town form-data))
               (seq (:state form-data)))
       (set-show-address? true)))

    (hooks/use-effect
     [show-form]
     (if-not show-form
       (do
         (set-editing-stand nil)
         (set-show-address? false)
         (set-form-data
          {:name ""
           :coordinate (str
                        (first map-home)
                        ", "
                        (second map-home))
           :address ""
           :town ""
           :state ""
           :products []
           :expiration (utils/in-a-week)
           :notes ""
           :shared? true}))
       (do
         (.addEventListener
          js/document
          "keydown"
          (fn [e]
            (when (= (.-key e) "Escape")
              (set-show-form false))))
         (.focus @coordinate-input-ref)
         (when (nil? editing-stand)
           (set-form-data
            (fn [prev]
              (assoc
               prev
               :coordinate (str
                            (first (or user-map-center map-home))
                            ", "
                            (second (or user-map-center map-home))))))))))

    (when show-form
      (d/div
       {:class "form-overlay"
        :onClick #(set-show-form false)}
       (d/form
        {:class "form-container"
         :onClick #(.stopPropagation %)
         :onSubmit (fn [e]
                     (.preventDefault e)
                     (let [all-unique-products (utils/get-all-unique-products stands)
                           stand-name (:name form-data)
                           updated-products (reduce
                                             (fn [acc product]
                                               (if (and
                                                    (str/includes?
                                                     (str/lower-case stand-name)
                                                     (str/lower-case product))
                                                    (not (some #(= % product) acc)))
                                                 (conj acc product)
                                                 acc))
                                             (:products form-data)
                                             all-unique-products)
                           processed-form-data (assoc form-data :products updated-products)]
                       (if editing-stand
                         (do
                           (set-stands (partial update-stand processed-form-data editing-stand))
                           (set-show-form false))
                         (let [new-stands (add-stand processed-form-data stands)]
                           (set-stands new-stands)
                           (when (not= new-stands stands)
                             (set-show-form false))))))}
        (d/div
         {:class "form-header-actions"}
         (d/h3 (if editing-stand "Edit Stand" "Add New Stand"))
         (d/div {:class "form-header-buttons"}
                (d/button
                 {:type "button"
                  :class "button icon-button"
                  :on-click #(set-show-form false)
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
             :form-data form-data
             :set-form-data set-form-data
             :location-btn-ref location-btn-ref
             :stands stands
             :map-home map-home
             :add-zoom-level add-zoom-level})
         ($ product-input
            {:form-data form-data
             :set-form-data set-form-data
             :current-product current-product
             :set-current-product set-current-product})
         (d/div
          {:class "form-group"}
          (d/label "Stand Name:")
          (d/input
           {:type "text"
            :value (:name form-data)
            :onChange #(set-form-data
                        (fn [prev]
                          (assoc prev :name (.. % -target -value))))}))
         (d/div
          {:class "form-group"}
          (d/label "Notes:")
          (d/textarea
           {:value (:notes form-data)
            :onChange #(set-form-data
                        (fn [prev]
                          (assoc prev :notes (.. % -target -value))))
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
               :value (:address form-data)
               :onChange #(set-form-data
                           (fn [prev]
                             (assoc prev :address (.. % -target -value))))}))
            (d/div
             {:class "form-group"}
             (d/label "Town:")
             (d/input
              {:type "text"
               :value (:town form-data)
               :onChange #(set-form-data
                           (fn [prev]
                             (assoc prev :town (.. % -target -value))))}))
            (d/div
             {:class "form-group"}
             (d/label "State:")
             (d/input
              {:type "text"
               :value (:state form-data)
               :onChange #(set-form-data
                           (fn [prev]
                             (assoc prev :state (.. % -target -value))))}))))
         (d/div
          {:class "form-group"}
          (d/label "Expiration Date:")
          (d/input
           {:type "date"
            :value (:expiration form-data)
            :onChange #(set-form-data
                        (fn [prev]
                          (assoc prev :expiration (.. % -target -value))))}))
         (d/div
          {:class "form-group"}
          (d/label {:for "shared-checkbox"} "Shared?")
          (d/input
           {:id "shared-checkbox"
            :class "checkbox"
            :type "checkbox"
            :checked (get form-data :shared? false)
            :onChange #(set-form-data
                        (fn [prev]
                          (assoc prev :shared? (.. % -target -checked))))}))))))))

(defnc settings-dialog
  [{:keys [show-settings-dialog
           set-show-settings-dialog
           form-data
           set-form-data
           set-settings]}]
  (when show-settings-dialog
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
       (d/div
        {:class "form-group"}
        (d/label "Resource:")
        (d/input
         {:type "text"
          :value (:resource form-data)
          :onChange #(set-form-data
                      (fn [prev]
                        (assoc prev :resource (.. % -target -value))))}))
       (d/div
        {:class "form-group"}
        (d/label "User:")
        (d/input
         {:type "text"
          :value (:user form-data)
          :onChange #(set-form-data
                      (fn [prev] (assoc prev :user (.. % -target -value))))}))
       (d/div
        {:class "form-group"}
        (d/label "Password:")
        (d/input
         {:type "password"
          :value (:password form-data)
          :onChange #(set-form-data
                      (fn [prev] (assoc prev :password (.. % -target -value))))}))
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
                      (set-settings form-data)
                      (set-show-settings-dialog false))}
         "Save")))))))
