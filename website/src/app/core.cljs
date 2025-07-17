(ns app.core
  (:require ["react-dom/client" :as rdom]
            [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]))


(defnc App []
  (let [[stands set-stands] (hooks/use-state [])
        [show-form set-show-form] (hooks/use-state false)
        [form-data set-form-data] (hooks/use-state {:name "" :location ""})
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
                                 (set-form-data {:name "" :location ""})))]
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
                                   (set-form-data {:name "" :location ""})
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
                (d/div {:class "form-buttons"}
                  (d/button {:type "submit"} "Add Stand")
                  (d/button {:type "button"
                             :onClick #(do (set-show-form false)
                                           (set-form-data {:name "" :location ""}))}
                            "Cancel"))))))
        
        (d/div {:class "stands-list"}
          (if (empty? stands)
            (d/p "No stands added yet.")
            (map (fn [stand]
                   (d/div {:key (str (:name stand) "-" (:location stand))
                           :class "stand-item"}
                     (d/h4 (:name stand))
                     (d/p (:location stand))))
                 stands)))))))

(defn init []
  (let [root (.createRoot rdom (js/document.getElementById "app"))]
    (.render root ($ App))))