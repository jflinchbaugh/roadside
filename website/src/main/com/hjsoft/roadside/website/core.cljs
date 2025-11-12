(ns com.hjsoft.roadside.website.core
  (:require ["react-dom/client" :as rdom]
            [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [taoensso.telemere :as tel]
            [cljs.reader]
            [clojure.string :as str]
            [clojure.edn :as edn]
            ["leaflet" :as L]))

(def map-home [40.0379 -76.3055])
(def add-zoom-level 16)
(def initial-zoom-level 11)

; utils

(defn get-current-timestamp []
  (.toISOString (js/Date.)))

(defn in-a-week []
  (let [date (js/Date.)
        week-later (+ (.getTime date) (* 7 24 60 60 1000))]
    (.toISOString (js/Date. week-later))
    (.substring (.toISOString (js/Date. week-later)) 0 10)))

(defn stand-key
  [stand]
  (->>
   stand
   ((juxt
     :name
     :coordinate
     :address
     :town
     :state
     :products))
   flatten
   (str/join "-")))

(defn get-all-unique-products [stands]
  (->> stands
       (mapcat :products)
       (map str/trim)
       (filter some?)
       distinct
       sort
       vec))

(defn parse-coordinates
  [coords]
  (let [res (->>
             (str/split coords #", *")
             (map str/trim)
             (map parse-double)
             (remove nil?))]
    (when (= 2 (count res)) res)))

(defn make-marker
  [{:keys [coord stand set-selected-stand]}]
  (let [marker (L/marker (clj->js coord))
        popup-content (str
                       (when (not (str/blank? (:name stand)))
                         (str "<b>" (:name stand) "</b><br>"))
                       (when (seq (:products stand))
                         (str
                          (str/join
                           ", "
                           (:products stand))
                          "<br>")))]
    (.bindPopup
     marker
     popup-content
     (clj->js {"autoPanPadding" (L/point 100 100)}))
    (.on marker "click" #(set-selected-stand stand))
    [stand marker]))

(defn make-current-location-marker
  [coord]
  (L/circleMarker (clj->js coord)
                  (clj->js {:radius 6
                            :color "#ffffff"
                            :fillColor "#3388ff"
                            :fillOpacity 0.8
                            :weight 1})))

(defn- init-map [div-id center zoom-level]
  (let [m (L/map div-id)
        tl (L/tileLayer
            "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png")]
    (.setView m (clj->js center) zoom-level)
    (.addTo tl m)
    m))

(defn make-map-link [coordinate-str]
  (when coordinate-str
    (let [[lat lng] (str/split coordinate-str #", *")]
      (when (and lat lng)
        (str "geo:" (str/trim lat) "," (str/trim lng))))))

;; actions

(defn add-product-to-form-data
  [current-product set-form-data]
  (when (not= current-product "")
    (set-form-data
     (fn [prev]
       (if (some #(= % current-product) (:products prev))
         prev
         (assoc
          prev
          :products (conj (:products prev) current-product)))))))

(defn update-stand
  [form-data editing-stand current-stands]
  (vec
   (map
    #(if
      (= % editing-stand)
       (assoc form-data :updated (get-current-timestamp))
       %)
    current-stands)))

(defn add-stand
  [form-data current-stands]
  (if
   (some
    #(=
      (stand-key form-data)
      (stand-key %))
    current-stands)
    (do
      (js/alert
       "This stand already exists!")
      current-stands)
    (conj current-stands (assoc form-data :updated (get-current-timestamp)))))

; components

(defnc leaflet-map
  [{:keys [div-id
           center
           zoom-level
           stands
           selected-stand
           set-selected-stand
           show-crosshairs
           set-coordinate-form-data
           map-ref
           is-locating
           current-location-coords]}]
  (let [[stand-map set-stand-map] (hooks/use-state nil)
        [layer-group set-layer-group] (hooks/use-state nil)
        [current-location-marker set-current-location-marker] (hooks/use-state nil)]
    (hooks/use-effect
     [center stand-map]
     (when stand-map
       (.setView
        ^js stand-map
        (clj->js center)
        (.getZoom ^js stand-map)
        (clj->js {:animate false}))))

    (hooks/use-effect
     :once
     (let [m (init-map div-id center zoom-level)]
       (when set-coordinate-form-data
         (.on
          m
          "moveend"
          (fn []
            (let [center (.getCenter m)]
              (set-coordinate-form-data
               (str (.-lat center) ", " (.-lng center)))))))
       (when map-ref
         (reset! map-ref m))
       (set-stand-map m)))

    (hooks/use-effect
     [stands selected-stand stand-map]
     (when stand-map
       (let [locations (->>
                        stands
                        (map (fn [s]
                               {:coord (parse-coordinates (:coordinate s))
                                :stand s}))
                        (remove (comp nil? :coord))
                        (map
                         (fn [{:keys [coord stand]}]
                           (make-marker
                            {:coord coord
                             :stand stand
                             :set-selected-stand (or
                                                  set-selected-stand
                                                  (constantly nil))}))))
             new-layer-group (when (seq locations)
                               (L/layerGroup
                                (clj->js (map second locations))))]
         (when layer-group
           (.removeLayer ^js stand-map layer-group))
         (when new-layer-group
           (.addTo ^js new-layer-group stand-map)
           (set-layer-group new-layer-group)
           (some->>
            locations
            (filter
             (fn [[s _]]
               (= (stand-key selected-stand) (stand-key s))))
            first
            second
            (#(.openPopup ^js %)))))))

    (hooks/use-effect
     [current-location-coords is-locating stand-map]
     (when stand-map
       (when current-location-marker
         (.removeLayer ^js stand-map current-location-marker))
       (when current-location-coords
         (let [marker (make-current-location-marker current-location-coords)]
           (.addTo ^js marker stand-map)
           (set-current-location-marker marker))))))
  (d/div {:id div-id
          :style {:position "relative"}}
         (when show-crosshairs
           (d/div {:class "crosshairs"}))
         (when is-locating
           (d/div
            {:class "loading-overlay"}
            (d/div {:class "spinner"})
            (d/p "Locating...")))))

(defnc stands-list
  [{:keys
    [stands
     set-stands
     set-editing-stand
     set-form-data
     set-show-form
     selected-stand
     set-selected-stand
     set-is-locating-main-map]}]
  (let [stand-refs (hooks/use-ref {})]
    (hooks/use-effect
     [selected-stand]
     (when selected-stand
       (when-let [stand-el (get @stand-refs (stand-key selected-stand))]
         (.scrollIntoView
          stand-el
          (clj->js {:behavior "smooth" :block "nearest"})))))

    (d/div
     {:class "stands-list"}
     (if (empty? stands)
       (d/p "No stands added yet.")
       (map
        (fn [stand]
          (d/div
           {:key (stand-key stand)
            :ref (fn [el] (swap! stand-refs assoc (stand-key stand) el))
            :class (str
                    "stand-item"
                    (when (= (stand-key stand) (stand-key selected-stand))
                      " selected-stand"))
            :onClick #(set-selected-stand stand)}
           (d/div
            {:class "stand-content"}
            (when (seq (:name stand))
              (d/div
               {:class "stand-header"}
               (d/h4 (:name stand))))
            (when (seq (:coordinate stand))
              (d/p {:class "coordinate-text"} (:coordinate stand)))
            (when (seq (:address stand))
              (d/p (:address stand)))
            (let [town-state (remove empty? [(:town stand) (:state stand)])]
              (when (seq town-state)
                (d/p (str/join ", " town-state))))
            (when (seq (:products stand))
              (d/div
               {:class "stand-products"}
               (d/strong "Products: ")
               (d/div
                {:class "products-tags"}
                (map (fn [product]
                       (d/span
                        {:key product
                         :class "product-tag"}
                        product))
                     (:products stand)))))
            (when (seq (:notes stand))
              (d/p
               {:class "stand-notes"}
               (d/strong "Notes: ")
               (:notes stand)))
            (when (seq (:expiration stand))
              (d/p
               {:class "expiration-date"}
               (d/strong "Expires: ")
               (:expiration stand)))
            (when (seq  (:updated stand))
              (d/p
               {:class "stand-updated"}
               (d/strong "Last Updated: ")
               (:updated stand))))
           (d/div
            {:class "stand-actions"}
            (when-let [map-link (make-map-link (:coordinate stand))]
              (d/a {:href map-link
                    :target "_blank"
                    :rel "noopener noreferrer"
                    :class "go-stand-btn"}
                   "Go"))
            (d/button
             {:class "edit-stand-btn"
              :onClick #(do (set-editing-stand stand)
                            (set-form-data
                             (assoc stand
                                    :town (:town stand)
                                    :state (:state stand)
                                    :address (:address stand)
                                    :notes (:notes stand)))
                            (set-show-form true)
                            (set-is-locating-main-map false))
              :title "Edit this stand"}
             "Edit")
            (d/button
             {:class "delete-stand-btn"
              :onClick #(set-stands (fn [current-stands]
                                      (->> current-stands
                                           (remove #{stand})
                                           vec)))
              :title "Delete this stand"}
             "Delete"))))
        stands)))))

(defnc location-input
  [{:keys
    [coordinate-input-ref
     is-locating
     set-is-locating
     form-data
     set-form-data
     location-btn-ref
     stands]}]
  (let [[location-error set-location-error] (hooks/use-state nil)
        [coordinate-display set-coordinate-display] (hooks/use-state
                                                     (:coordinate form-data))
        map-ref (hooks/use-ref nil)
        [current-location set-current-location] (hooks/use-state nil)]
    (hooks/use-effect
     [map-ref (:coordinate form-data)]
     (when-let [m @map-ref]
       (when-let [coords (parse-coordinates (:coordinate form-data))]
         (let [current-zoom (.getZoom ^js m)]
           (.setView ^js m (clj->js coords) current-zoom)))))

    (hooks/use-effect
     [(:coordinate form-data)]
     (set-coordinate-display (:coordinate form-data)))

    (d/div
     {:class "form-group"}
     ($ leaflet-map
        {:div-id "map-form"
         :center (or (parse-coordinates (:coordinate form-data)) map-home)
         :zoom-level add-zoom-level
         :stands stands
         :show-crosshairs true
         :set-coordinate-form-data (fn [coord-str]
                                     (set-form-data
                                      (fn [prev]
                                        (assoc prev :coordinate coord-str))))
         :map-ref map-ref
         :is-locating is-locating
         :current-location-coords current-location})
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
        :ref location-btn-ref ; Apply the ref here
        :onClick (fn []
                   (set-location-error nil)
                   (set-is-locating true)
                   (js/navigator.geolocation.getCurrentPosition
                    (fn [position]
                      (let [coords (.-coords position)
                            lat (.-latitude coords)
                            lng (.-longitude coords)]
                        (set-current-location [lat lng])
                        (set-form-data
                         (fn [prev]
                           (assoc
                            prev
                            :coordinate (str lat ", " lng))))
                        (set-is-locating false)))
                    (fn [error]
                      (tel/log! :error
                                {:failed-location (.-message error)})
                      (set-location-error
                       "Please try again or enter location manually.")
                      (set-is-locating false))))}
       "\u2316"))
     (when location-error
       (d/p
        {:class "error-message"}
        location-error)))))

(defnc stand-form
  [{:keys [form-data
           set-form-data
           editing-stand
           set-editing-stand
           show-form
           set-show-form
           stands
           set-stands]}]
  (let [[current-product set-current-product] (hooks/use-state "")
        [is-locating set-is-locating] (hooks/use-state false)
        coordinate-input-ref (hooks/use-ref nil)
        location-btn-ref (hooks/use-ref nil)]
    (hooks/use-effect
     [show-form]
     (when-not show-form
       (set-editing-stand nil)
       (set-form-data
        {:name ""
         :coordinate (str (first map-home) ", " (second map-home))
         :address ""
         :town ""
         :state ""
         :products []
         :expiration (in-a-week)
         :notes ""})))

    (hooks/use-effect
     [show-form]
     (when show-form
       (.addEventListener
        js/document
        "keydown"
        (fn [e]
          (when (= (.-key e) "Escape")
            (set-show-form false))))
       (.focus @coordinate-input-ref)
       ;; Simulate click on location button ONLY if adding a new stand
       (when
        (and
         (nil? editing-stand)
         (when-let [btn @location-btn-ref] btn))
         (.click @location-btn-ref))))

    (when show-form
      (d/div
       {:class "form-overlay"
        :onClick #(set-show-form false)}
       (d/form
        {:class "form-container"
         :onClick #(.stopPropagation %)
         :onSubmit (fn [e]
                     (.preventDefault e)
                     (let [all-unique-products (get-all-unique-products
                                                stands)
                           stand-name (:name form-data)
                           updated-products (reduce
                                             (fn [acc product]
                                               (if
                                                (and
                                                 (str/includes?
                                                  (str/lower-case stand-name)
                                                  (str/lower-case product))
                                                 (not
                                                  (some
                                                   #(= % product) acc)))
                                                 (conj acc product)
                                                 acc))
                                             (:products form-data)
                                             all-unique-products)
                           processed-form-data (assoc
                                                form-data
                                                :products
                                                updated-products)]
                       (if editing-stand
                          ;; Update existing stand
                         (do
                           (set-stands
                            (partial
                             update-stand
                             processed-form-data
                             editing-stand))
                           (set-show-form false))
                          ;; Add new stand
                         (let [new-stands (add-stand
                                           processed-form-data
                                           stands)]
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
             :is-locating is-locating
             :set-is-locating set-is-locating
             :form-data form-data
             :set-form-data set-form-data
             :location-btn-ref location-btn-ref
             :stands stands})
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
                    "×")))
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
                            (set-current-product "")))})
           (d/button
            {:type "button"
             :class "add-product-btn"
             :onClick (fn []
                        (add-product-to-form-data
                         current-product
                         set-form-data)
                        (set-current-product ""))}
            "Add")))
         (d/div
          {:class "form-group"}
          (d/label "Stand Name:")
          (d/input
           {:type "text"
            :value (:name form-data)
            :onChange #(set-form-data
                        (fn [prev]
                          (assoc
                           prev
                           :name (.. % -target -value))))}))
         (d/div
          {:class "form-group"}
          (d/label "Address:")
          (d/input
           {:type "text"
            :value (:address form-data)
            :onChange #(set-form-data
                        (fn [prev]
                          (assoc
                           prev
                           :address (.. % -target -value))))}))
         (d/div
          {:class "form-group"}
          (d/label "Town:")
          (d/input
           {:type "text"
            :value (:town form-data)
            :onChange #(set-form-data
                        (fn [prev]
                          (assoc
                           prev
                           :town (.. % -target -value))))}))
         (d/div
          {:class "form-group"}
          (d/label "State:")
          (d/input
           {:type "text"
            :value (:state form-data)
            :onChange #(set-form-data
                        (fn [prev]
                          (assoc
                           prev
                           :state (.. % -target -value))))}))
         (d/div
          {:class "form-group"}
          (d/label "Notes:")
          (d/textarea
           {:value (:notes form-data)
            :onChange #(set-form-data
                        (fn [prev]
                          (assoc
                           prev
                           :notes (.. % -target -value))))
            :rows 4}))
         (d/div
          {:class "form-group"}
          (d/label "Expiration Date:")
          (d/input
           {:type "date"
            :value (:expiration form-data)
            :onChange #(set-form-data
                        (fn [prev]
                          (assoc
                           prev
                           :expiration (.. % -target -value))))}))))))))

(defnc header []
  (d/header
   {:class "header"}
   (d/img
    {:src "images/apples.png"
     :alt "Apple Logo"
     :class "logo"})
   (d/h1
    {:class "main-header"}
    "Roadside Stands"
    " "
    (d/span {:style {:font-size "0.5em"}} "beta"))))

(defnc fixed-header [{:keys [children]}]
  (d/div
   {:id "fixed-header"}
   children))

(defnc product-list
  [{:keys [stands
           set-product-filter
           product-filter]}]
  (let [all-products (flatten (map :products stands))
        unique-products (sort (distinct all-products))]
    (d/div
     {:class "product-list"}
     (if (empty? unique-products)
       (d/p "No products available yet.")
       (d/div
        {:class "products-tags"}
        (map (fn [product]
               (d/span
                {:key product
                 :class (str
                         "product-tag"
                         (when (= product product-filter)
                           " product-tag-active"))
                 :onClick #(if (= product product-filter)
                             (set-product-filter nil)
                             (set-product-filter product))}
                product))
             unique-products)))
     (when product-filter
       (d/button
        {:class "clear-filter-btn"
         :onClick #(set-product-filter nil)}
        "Clear Filter")))))

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

(defnc app []
  (let [[stands set-stands] (hooks/use-state [])
        [show-form set-show-form] (hooks/use-state false)
        [editing-stand set-editing-stand] (hooks/use-state nil)
        [stand-form-data set-stand-form-data] (hooks/use-state {})
        [product-filter set-product-filter] (hooks/use-state nil)
        [selected-stand set-selected-stand] (hooks/use-state nil)
        [current-location set-current-location] (hooks/use-state map-home)
        [is-locating-main-map set-is-locating-main-map] (hooks/use-state true)
        [main-map-location-error set-main-map-location-error] (hooks/use-state nil)
        [show-settings-dialog set-show-settings-dialog] (hooks/use-state false)
        [settings-form-data set-settings-form-data] (hooks/use-state
                                                      {:resource ""
                                                       :user ""
                                                       :password ""})
        [settings set-settings] (hooks/use-state {})
        filtered-stands (let [sorted-stands (sort-by :updated #(compare %2 %1) stands)]
                          (if product-filter
                            (filter
                             #(some
                               (fn [p] (= p product-filter))
                               (:products %))
                             sorted-stands)
                            sorted-stands))
        locate-main-map (fn []
                          (set-main-map-location-error nil)
                          (set-is-locating-main-map true)
                          (js/navigator.geolocation.getCurrentPosition
                           (fn [position]
                             (let [coords (.-coords position)
                                   lat (.-latitude coords)
                                   lng (.-longitude coords)]
                               (set-current-location [lat lng])
                               (set-is-locating-main-map false)))
                           (fn [error]
                             (tel/log! :error
                                       {:failed-location (.-message error)})
                             (set-main-map-location-error "No Location")
                             (set-is-locating-main-map false))))]

    (hooks/use-effect
     :once
     (let [saved-stands (js/localStorage.getItem "roadside-stands")]
       (when saved-stands
         (set-stands (edn/read-string saved-stands)))))

    (hooks/use-effect
     [stands]
     (js/localStorage.setItem "roadside-stands" (pr-str stands)))

    (hooks/use-effect
     :once
     (let [saved-settings (js/localStorage.getItem "roadside-settings")]
       (when saved-settings
         (set-settings-form-data (edn/read-string saved-settings))
         (set-settings (edn/read-string saved-settings)))))

    (hooks/use-effect
     [settings]
     (let [to-save (pr-str settings)]
       (js/localStorage.setItem "roadside-settings" to-save)))

    (hooks/use-effect
     :once
     (locate-main-map))

    (d/div
     {:class "app-container"}

     ($ fixed-header
        ($ header)
        ($ leaflet-map
           {:div-id "map-container"
            :center current-location
            :stands filtered-stands
            :zoom-level initial-zoom-level
            :set-selected-stand set-selected-stand
            :selected-stand selected-stand
            :is-locating is-locating-main-map
            :current-location-coords current-location}))
     (d/div
      {:class "content"}
      (d/div
       {:class "main-actions"}
       (d/button
        {:class "add-stand-btn"
         :onClick #(do (set-show-form true)
                       (set-is-locating-main-map false))}
        "Add Stand")
       (d/div
        {:class "map-actions-right"}
        (when main-map-location-error
          (d/p {:class "error-message"} main-map-location-error))
        (d/button
         {:type "button"
          :class "location-btn"
          :onClick locate-main-map}
         "\u2316")))
      ($ product-list
         {:stands stands
          :set-product-filter set-product-filter
          :product-filter product-filter})
      ($ stand-form
         {:form-data stand-form-data
          :set-form-data set-stand-form-data
          :show-form show-form
          :set-show-form set-show-form
          :editing-stand editing-stand
          :set-editing-stand set-editing-stand
          :stands stands
          :set-stands set-stands})
      ($ stands-list
         {:stands filtered-stands
          :set-stands set-stands
          :set-editing-stand set-editing-stand
          :set-form-data set-stand-form-data
          :set-show-form set-show-form
          :selected-stand selected-stand
          :set-selected-stand set-selected-stand
          :set-is-locating-main-map set-is-locating-main-map})
      (d/button
       {:class "settings-btn"
        :onClick #(set-show-settings-dialog true)}
       "\u2699")
      ($ settings-dialog
         {:show-settings-dialog show-settings-dialog
          :set-show-settings-dialog set-show-settings-dialog
          :form-data settings-form-data
          :set-form-data set-settings-form-data
          :set-settings set-settings})))))

(defn init []
  (let [root (.createRoot rdom (js/document.getElementById "app"))]
    (.render root ($ app))))
