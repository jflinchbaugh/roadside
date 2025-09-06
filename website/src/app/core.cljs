(ns app.core
  (:require ["react-dom/client" :as rdom]
            [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [taoensso.telemere :as tel]
            [cljs.reader]
            [clojure.string :as str]
            [clojure.edn :as edn]))

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
     :coordinates
     :address
     :town
     :state
     :products))
   flatten
   (str/join "-")))

(defn parse-coordinates
  [coords]
  (->> (str/split coords #", *")
       (map str/trim)
       (map parse-double)))

(defn make-marker
  [coord]
  (let [marker (js/L.marker (clj->js coord))]
    marker))

(defn- init-map []
  (let [m (js/L.map "map-container")
        tl (js/L.tileLayer
            "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png")]
    (.setView m (clj->js map-home) 10)
    (.addTo tl m)
    m))

; components

(defnc leaflet-map [{:keys [stands]}]
  (let [[stand-map set-stand-map] (hooks/use-state nil)
        [layer-group set-layer-group] (hooks/use-state nil)]
    (hooks/use-effect
     :once
     (set-stand-map (init-map)))
    (hooks/use-effect
     [stands]
     (tel/log! :info {:effect-stands stands})
     (let [locations (->>
                      stands
                      (map :coordinate)
                      (map parse-coordinates)
                      (remove #{[nil]})
                      (map make-marker))
           new-layer-group (when (not (empty? locations))
                             (js/L.layerGroup (clj->js locations)))
           _ (tel/log! :info {:locations-to-map locations})]
       (when layer-group
         (.removeLayer ^js stand-map layer-group))
       (when new-layer-group
         (tel/log! :info {:layer-group new-layer-group})
         (.addTo ^js new-layer-group stand-map)
         (set-layer-group new-layer-group)))))
  (d/div {:id "map-container"}))

(defnc stands-list
  [{:keys [stands set-stands set-editing-stand set-form-data set-show-form]}]
  (d/div
   {:class "stands-list"}
   (if (empty? stands)
     (d/p "No stands added yet.")
     (map
      (fn [stand]
        (d/div
         {:key (stand-key stand)
          :class "stand-item"}
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
                                     (vec (remove #{stand} current-stands))))
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

(defnc stand-form
  [{:keys [form-data
           set-form-data
           editing-stand
           show-form
           set-show-form
           set-editing-stand
           coordinate-input-ref
           stands
           set-stands]}]
  (let [[current-product set-current-product] (hooks/use-state "")
        [location-error set-location-error] (hooks/use-state nil)
        [is-locating set-is-locating] (hooks/use-state false)]
    (hooks/use-effect
     [show-form]
     (when show-form
       (.focus @coordinate-input-ref)))

    (hooks/use-effect
     [show-form]
     (when-not show-form
       (set-editing-stand nil)
       (set-form-data
        {:name ""
         :coordinate ""
         :address ""
         :town ""
         :state ""
         :products []
         :expiration (in-a-week)
         :notes ""})))

    (hooks/use-effect
     [show-form]
     (when show-form
       (let [handle-keydown (fn [e]
                              (when (= (.-key e) "Escape")
                                (set-show-form false)))]
         (.addEventListener js/document "keydown" handle-keydown))))

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
                        (set-stands (fn [current-stands]
                                      (vec
                                       (map
                                        #(if
                                          (= % editing-stand)
                                           form-data
                                           %)
                                        current-stands))))
                           ;; Add new stand
                        (set-stands (fn [current-stands]
                                      (let [new-stand-key (stand-key form-data)]
                                        (if (some #(= new-stand-key (stand-key %)) current-stands)
                                          (do (js/alert "This stand already exists!")
                                              current-stands) ; Return current-stands unchanged
                                          (conj current-stands form-data))))))
                      (set-show-form false))}
         (d/div
          {:class "form-group"}
          (d/label "Coordinate:")
          (d/div
           {:class "coordinate-input-group"}
           (d/input
            {:type "text"
             :ref coordinate-input-ref
             :value (:coordinate form-data)
             :onChange #(set-form-data
                         (fn [prev]
                           (assoc
                            prev
                            :coordinate (.. % -target -value))))
             :class "coordinate-input"})
           (d/button
            {:type "button"
             :class "location-btn"
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
            "\u2316")))
         (when location-error
           (d/p
            {:class "error-message"}
            location-error))
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

(defnc app []
  (let [[stands set-stands] (hooks/use-state [])
        [show-form set-show-form] (hooks/use-state false)
        [editing-stand set-editing-stand] (hooks/use-state nil)
        [form-data set-form-data] (hooks/use-state {})
        coordinate-input-ref (hooks/use-ref nil)]

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
     (d/header
      {:class "header"}
      (d/img
       {:src "images/apples.png"
        :alt "Apple Logo"
        :class "logo"})
      (d/h1
       {:class "main-header"}
       "Roadside Stands"))

     (d/div
      {:class "content"}
      ($ leaflet-map {:stands stands})

      (d/button
       {:class "add-stand-btn"
        :onClick #(set-show-form true)}
       "Add Stand")

      ($ stand-form
         {:form-data form-data
          :set-form-data set-form-data
          :show-form show-form
          :set-show-form set-show-form
          :editing-stand editing-stand
          :set-editing-stand set-editing-stand
          :coordinate-input-ref coordinate-input-ref
          :stands stands
          :set-stands set-stands})

      ($ stands-list
         {:stands stands
          :set-stands set-stands
          :set-editing-stand set-editing-stand
          :set-form-data set-form-data
          :set-show-form set-show-form})))))

(defn init []
  (let [root (.createRoot rdom (js/document.getElementById "app"))]
    (.render root ($ app))))
