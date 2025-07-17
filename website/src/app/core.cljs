(ns app.core
  (:require ["react-dom/client" :as rdom]
            [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]))


(defnc App []
  (d/div {:class "app-container"}
    (d/header {:class "header"}
      (d/img {:src "/images/apples.png" 
              :alt "Apple Logo" 
              :class "logo"})
      (d/h1 {:class "main-header"} "Roadside Stands"))
    (d/p {:class "ready-message"} "Ready to build something awesome!")))

(defn init []
  (let [root (.createRoot rdom (js/document.getElementById "app"))]
    (.render root ($ App))))