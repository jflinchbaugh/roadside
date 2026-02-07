(ns com.hjsoft.roadside.website.ui.layout
  (:require [helix.core :refer [defnc]]
            [helix.hooks :as hooks]
            [helix.dom :as d]))

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

(defnc notification-toast
  [{:keys [notification set-notification]}]
  (hooks/use-effect
   [notification]
   (when notification
     (let [timer (js/setTimeout
                  #(set-notification nil)
                  3000)]
       (fn [] (js/clearTimeout timer)))))
  (when notification
    (d/div
     {:class (str "notification-toast " (name (:type notification)))}
     (:message notification))))
