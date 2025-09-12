(ns app.core
  (:require ["react-dom/client" :as rdom]
            [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [taoensso.telemere :as tel]
            [cljs.reader]
            [clojure.string :as str]
            [clojure.edn :as edn]))

(defnc product-list [{:keys [stands set-product-filter product-filter]}]
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
                 :class (str "product-tag" (when (= product product-filter) " product-tag-active"))
                 :onClick #(set-product-filter product)}
                product))
             unique-products))))))

(def map-home [40.0379 -76.3055])

; utils
(defn in-a-week []
  (let [date (js/Date.)
        week-later (+ (.getTime date) (* 7 24 60 60 1000))]
    (.toISOString (js/Date. week-later))
    (.substring (.toISOString (js/Date. week-later)) 0 10)))

(defn add-product-to-form-data
  [current-product form-data set-form-data]
  (when (not= current-product "")
    (set-form-data
     (fn [prev]
       (if (some #(= % current-product) (:products prev))
         prev
         (assoc
          prev
          :products (conj (:products prev) current-product)))))))

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

(defn parse-coordinates
  [coords]
  (let [res (->>
             (str/split coords #", *")
             (map str/trim)
             (map parse-double))]
    (when (= 2 (count res)) res)))

(defn make-marker
  [{:keys [coord stand set-selected-stand]}]
  (let [marker (js/L.marker (clj->js coord))
        popup-content (str
                       (when (not (str/blank? (:name stand)))
                         (str "<b>" (:name stand) "</b><br>"))
                       (when (not (empty? (:products stand)))
                         (str
                          (str/join
                           ", "
                           (:products stand))
                          "<br>")))]
    (.bindPopup
     marker
     popup-content
     (clj->js {"autoPanPadding" (js/L.point 100 100)}))
    (.on marker "click" #(set-selected-stand stand))
    [stand marker]))

(defn- init-map [div-id center zoom-level]
  (let [m (js/L.map div-id)
        tl (js/L.tileLayer
            "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png")]
    (.setView m (clj->js center) zoom-level)
    (.addTo tl m)
    m))

(defn update-stand
  [form-data editing-stand current-stands]
  (vec
   (map
    #(if
      (= % editing-stand)
       form-data
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
    (conj current-stands form-data)))

; components

(defnc leaflet-map [{:keys [div-id center zoom-level stands selected-stand set-selected-stand show-crosshairs set-coordinate-form-data map-ref]}]
  (let [[stand-map set-stand-map] (hooks/use-state nil)
        [layer-group set-layer-group] (hooks/use-state nil)]
    (hooks/use-effect
     :once
     (let [m (init-map div-id center zoom-level)]
       (when set-coordinate-form-data
         (.on m "moveend" (fn []
                             (let [center (.getCenter m)]
                               (set-coordinate-form-data (str (.-lat center) ", " (.-lng center)))))))
       (when map-ref
         (reset! map-ref m))
       (set-stand-map m)))

    (hooks/use-effect
     [stands selected-stand]
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
                           :set-selected-stand set-selected-stand}))))
           _ (tel/log! :info {:locations locations})
           new-layer-group (when (not (empty? locations))
                             (js/L.layerGroup (clj->js (map second locations))))]
       (when layer-group
         (.removeLayer ^js stand-map layer-group))
       (when new-layer-group
         (.addTo ^js new-layer-group stand-map)
         (set-layer-group new-layer-group)
         (some->>
          locations
          (filter
           (fn [[s m]]
             (= (stand-key selected-stand) (stand-key s))))
          first
          second
          (#(.openPopup ^js %)))))))
  (d/div {:id div-id
          :style {:position "relative"}}
    (when show-crosshairs
      (d/div {:class "crosshairs"}))))

(defnc stands-list
  [{:keys
    [stands
     set-stands
     set-editing-stand
     set-form-data
     set-show-form
     selected-stand
     set-selected-stand]}]
  (d/div
   {:class "stands-list"}
   (if (empty? stands)
     (d/p "No stands added yet.")
     (map
      (fn [stand]
        (d/div
         {:key (stand-key stand)
          :class (str "stand-item" (when (= (stand-key stand) (stand-key selected-stand)) " selected-stand"))
          :onClick #(do (set-selected-stand stand))}
         (d/div
          {:class "stand-header"}
          (d/h4 (:name stand))
          (d/div
           {:class "stand-actions"}
           (d/button
            {:class "edit-stand-btn"
             :onClick #(do (set-editing-stand stand)
                           (set-form-data
                            (assoc stand
                                   :town (:town stand)
                                   :state (:state stand)
                                   :address (:address stand)
                                   :notes (:notes stand)))
                           (set-show-form true))
             :title "Edit this stand"}
            "Edit")
           (d/button
            {:class "delete-stand-btn"
             :onClick #(set-stands (fn [current-stands]
                                     (->> current-stands
                                          (remove #{stand})
                                          vec)))
             :title "Delete this stand"}
            "Delete")))
         (d/p (:coordinate stand))
         (when (not (empty? (:address stand)))
           (d/p (:address stand)))
         (when (not (empty? (:town stand)))
           (d/p (str (:town stand) ", " (:state stand))))
         (when (not (empty? (:expiration stand)))
           (d/p
            {:class "expiration-date"}
            (d/strong "Expires: ")
            (:expiration stand)))
         (when (not (empty? (:products stand)))
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
         (when (not (empty? (:notes stand)))
           (d/p
            {:class "stand-notes"}
            (d/strong "Notes: ")
            (:notes stand)))))
      stands))))

(defnc location-input
  [{:keys
    [coordinate-input-ref
     is-locating
     set-is-locating
     form-data
     set-form-data
     location-btn-ref]}] ; New prop
  (let [[location-error set-location-error] (hooks/use-state nil)
        [coordinate-display set-coordinate-display] (hooks/use-state (:coordinate form-data))
        map-ref (hooks/use-ref nil)]

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
         :zoom-level 14
         :show-crosshairs true
         :set-coordinate-form-data (fn [coord-str]
                                     (set-form-data
                                      (fn [prev]
                                        (assoc prev :coordinate coord-str))))
         :map-ref map-ref})
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
        location-btn-ref (hooks/use-ref nil)] ; New ref for the location button
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
       (when (and (nil? editing-stand) (when-let [btn @location-btn-ref] btn))
         (.click @location-btn-ref))))

    (when show-form
      (d/div
       {:class "form-overlay"
        :onClick #(set-show-form false)}
       (d/div
        {:class "form-container"
         :onClick #(.stopPropagation %)}
        (when is-locating
          (d/div
           {:id "progress-bar"}))
        (d/h3
         (if editing-stand "Edit Stand" "Add New Stand"))
        (d/form
         {:onSubmit (fn [e]
                      (.preventDefault e)
                      (if editing-stand
                        ;; Update existing stand
                        (do
                          (set-stands
                           (partial update-stand form-data editing-stand))
                          (set-show-form false))
                        ;; Add new stand
                        (let [new-stands (add-stand form-data stands)]
                          (set-stands new-stands)
                          (when (not= new-stands stands)
                            (set-show-form false)))))}
         ($ location-input
            {:coordinate-input-ref coordinate-input-ref
             :is-locating is-locating
             :set-is-locating set-is-locating
             :form-data form-data
             :set-form-data set-form-data
             :location-btn-ref location-btn-ref}) ; Pass the new ref
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
                             form-data
                             set-form-data)
                            (set-current-product "")))})
           (d/button
            {:type "button"
             :class "add-product-btn"
             :onClick (fn []
                        (add-product-to-form-data
                         current-product
                         form-data
                         set-form-data)
                        (set-current-product ""))}
            "Add")))
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
                           :expiration (.. % -target -value))))}))
         (d/div
          {:class "form-buttons"}
          (d/button
           {:type "submit"}
           (if editing-stand "Save Changes" "Add Stand"))
          (d/button
           {:type "button"
            :onClick #(set-show-form false)}
           "Cancel"))))))))

(defnc header []
  (d/header
   {:class "header"}
   (d/img
    {:src "images/apples.png"
     :alt "Apple Logo"
     :class "logo"})
   (d/h1
    {:class "main-header"}
    "Roadside Stands")))

(defnc app []
  (let [[stands set-stands] (hooks/use-state [])
        [show-form set-show-form] (hooks/use-state false)
        [editing-stand set-editing-stand] (hooks/use-state nil)
        [form-data set-form-data] (hooks/use-state {})
        [product-filter set-product-filter] (hooks/use-state nil)
        [selected-stand set-selected-stand] (hooks/use-state nil)]

    (hooks/use-effect
     :once
     (let [saved-stands (js/localStorage.getItem "roadside-stands")]
       (when saved-stands
         (set-stands (edn/read-string saved-stands)))))

    (hooks/use-effect
     [stands]
     (js/localStorage.setItem "roadside-stands" stands))

    (d/div
     {:class "app-container"}

     ($ header)

     (d/div
      {:class "content"}
      ($ leaflet-map
         {:div-id "map-container"
          :center map-home
          :stands (if product-filter
                    (filter #(some (fn [p] (= p product-filter)) (:products %)) stands)
                    stands)
          :zoom-level 10
          :set-selected-stand set-selected-stand
          :selected-stand selected-stand})

      (d/button
       {:class "add-stand-btn"
        :onClick #(set-show-form true)}
       "Add Stand")

      ($ product-list {:stands stands :set-product-filter set-product-filter :product-filter product-filter})

      (when product-filter
        (d/button
         {:class "clear-filter-btn"
          :onClick #(set-product-filter nil)}
         "Clear Filter"))

      ($ stand-form
         {:form-data form-data
          :set-form-data set-form-data
          :show-form show-form
          :set-show-form set-show-form
          :editing-stand editing-stand
          :set-editing-stand set-editing-stand
          :stands stands
          :set-stands set-stands})

      ($ stands-list
         {:stands (if product-filter
                    (filter #(some (fn [p] (= p product-filter)) (:products %)) stands)
                    stands)
          :set-stands set-stands
          :set-editing-stand set-editing-stand
          :set-form-data set-form-data
          :set-show-form set-show-form
          :selected-stand selected-stand
          :set-selected-stand set-selected-stand})))))

(defn init []
  (let [root (.createRoot rdom (js/document.getElementById "app"))]
    (.render root ($ app))))
