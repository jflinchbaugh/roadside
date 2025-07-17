(ns app.core
  (:require ["react-dom/client" :as rdom]
            [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]))


(defnc App []
  (let [default-expiration (let [date (js/Date.)
                                 week-later (+ (.getTime date) (* 7 24 60 60 1000))]
                             (.toISOString (js/Date. week-later))
                             (.substring (.toISOString (js/Date. week-later)) 0 10))
        [stands set-stands] (hooks/use-state [])
        [show-form set-show-form] (hooks/use-state false)
        [form-data set-form-data] (hooks/use-state {:name "" :location "" :products [] :expiration default-expiration})
        [current-product set-current-product] (hooks/use-state "")
        name-input-ref (hooks/use-ref nil)]
    
    (hooks/use-effect
      [show-form]
      (when show-form
        (.focus @name-input-ref)))
    
    (hooks/use-effect
      [show-form]
      (if show-form
        (let [handle-keydown (fn [e]
                               (when (= (.-key e) "Escape")
                                 (set-show-form false)
                                 (set-form-data {:name "" :location "" :products [] :expiration default-expiration})))]
          (.addEventListener js/document "keydown" handle-keydown)
          #(.removeEventListener js/document "keydown" handle-keydown))
        js/undefined))
    
    (d/div {:class "app-container"}
      (d/header {:class "header"}
        (d/img {:src "/images/apples.png" 
                :alt "Apple Logo" 
                :class "logo"})
        (d/h1 {:class "main-header"} "Roadside Stands"))
      
      (d/div {:class "content"}
        (d/button {:class "add-stand-btn"
                   :onClick #(set-show-form true)}
                  "Add Stand")
        
        (when show-form
          (d/div {:class "form-overlay"
                  :onClick #(do (set-show-form false)
                                (set-form-data {:name "" :location ""}))}
            (d/div {:class "form-container"
                    :onClick #(.stopPropagation %)}
              (d/h3 "Add New Stand")
              (d/form {:onSubmit (fn [e]
                                   (.preventDefault e)
                                   (set-stands #(conj % form-data))
                                   (set-form-data {:name "" :location "" :products [] :expiration default-expiration})
                                   (set-show-form false))}
                (d/div {:class "form-group"}
                  (d/label "Stand Name:")
                  (d/input {:type "text"
                            :ref name-input-ref
                            :value (:name form-data)
                            :onChange #(set-form-data (fn [prev] (assoc prev :name (.. % -target -value))))
                            :required true}))
                (d/div {:class "form-group"}
                  (d/label "Location:")
                  (d/input {:type "text"
                            :value (:location form-data)
                            :onChange #(set-form-data (fn [prev] (assoc prev :location (.. % -target -value))))
                            :required true}))
                (d/div {:class "form-group"}
                  (d/label "Products:")
                  (d/div {:class "products-tags"}
                    (map (fn [product]
                           (d/span {:key product :class "product-tag"}
                             product
                             (d/button {:type "button"
                                        :class "remove-tag"
                                        :onClick #(set-form-data (fn [prev] (assoc prev :products (vec (remove #{product} (:products prev))))))}
                                       "×")))
                         (:products form-data)))
                  (d/input {:type "text"
                            :value current-product
                            :placeholder "Add a product and press Enter"
                            :onChange #(set-current-product (.. % -target -value))
                            :onKeyDown (fn [e]
                                         (when (and (= (.-key e) "Enter") (not= current-product ""))
                                           (.preventDefault e)
                                           (set-form-data (fn [prev] (assoc prev :products (conj (:products prev) current-product))))
                                           (set-current-product "")))}))
                (d/div {:class "form-group"}
                  (d/label "Expiration Date:")
                  (d/input {:type "date"
                            :value (:expiration form-data)
                            :onChange #(set-form-data (fn [prev] (assoc prev :expiration (.. % -target -value))))}))
                (d/div {:class "form-buttons"}
                  (d/button {:type "submit"} "Add Stand")
                  (d/button {:type "button"
                             :onClick #(do (set-show-form false)
                                           (set-form-data {:name "" :location "" :products [] :expiration default-expiration}))}
                            "Cancel"))))))
        
        (d/div {:class "stands-list"}
          (if (empty? stands)
            (d/p "No stands added yet.")
            (map (fn [stand]
                   (d/div {:key (str (:name stand) "-" (:location stand))
                           :class "stand-item"}
                     (d/div {:class "stand-header"}
                       (d/h4 (:name stand))
                       (d/button {:class "delete-stand-btn"
                                  :onClick #(set-stands (fn [current-stands] 
                                                          (vec (remove #{stand} current-stands))))
                                  :title "Delete this stand"}
                                 "Delete"))
                     (d/p (:location stand))
                     (when (not (empty? (:expiration stand)))
                       (d/p {:class "expiration-date"} 
                            (d/strong "Expires: ")
                            (:expiration stand)))
                     (when (not (empty? (:products stand)))
                       (d/div {:class "stand-products"}
                         (d/strong "Products: ")
                         (d/div {:class "products-tags"}
                           (map (fn [product]
                                  (d/span {:key product :class "product-tag"} product))
                                (:products stand)))))))
                 stands)))))))

(defn init []
  (let [root (.createRoot rdom (js/document.getElementById "app"))]
    (.render root ($ App))))