(ns com.hjsoft.roadside.website.ui.layout
  (:require [helix.core :refer [defnc]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.state :as state]))

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

(defnc notification-toast []
  (let [{:keys [state dispatch]} (hooks/use-context state/app-context)
        {:keys [notification]} state]
    (hooks/use-effect
     [notification]
     (when notification
       (let [timer (js/setTimeout
                    #(dispatch [:set-notification nil])
                    3000)]
         (fn [] (js/clearTimeout timer)))))
    (when (and notification (not (:stand-id notification)))
      (d/div
       {:class (str "notification-toast " (name (:type notification)))}
       (:message notification)))))

(defnc stand-notification-toast [{:keys [stand-id]}]
  (let [{:keys [state dispatch]} (hooks/use-context state/app-context)
        {:keys [notification]} state]
    (hooks/use-effect
     [notification]
     (when (and notification (= (:stand-id notification) stand-id))
       (let [timer (js/setTimeout
                    #(dispatch [:set-notification nil])
                    3000)]
         (fn [] (js/clearTimeout timer)))))
    (when (and notification (= (:stand-id notification) stand-id))
      (d/div
       {:class (str "stand-notification-toast " (name (:type notification)))}
       (:message notification)))))
