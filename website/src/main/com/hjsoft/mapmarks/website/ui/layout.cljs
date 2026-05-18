(ns com.hjsoft.mapmarks.website.ui.layout
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.mapmarks.website.state :as state]))

(defnc loading-indicator []
  (let [{:keys [state]} (hooks/use-context state/app-context)
        {:keys [loading-marks?]} state]
    (when loading-marks?
      (d/div
       {:class "loading-indicator"}
       (d/div {:class "mini-spinner"})
       (d/span "Refreshing...")))))

(def info-icon "\u24d8")

(defnc header []
  (let [{:keys [state ui]} (state/use-app)
        {:keys [set-show-about-dialog]} ui
        {:keys [config]} state
        logo (:app-logo config)
        is-image? (re-find #"\.(png|jpg|jpeg|svg|webp|gif)$" (str logo))]
    (d/header
     {:class "header"}
     (if is-image?
       (d/img
        {:src logo
         :alt "Logo"
         :class "logo"})
       (d/span
        {:class "logo" :style {:font-size "2em" :margin-right "10px"}}
        logo))
     (d/h1
      {:class "main-header"}
      (:app-name config)
      " "
      (d/span {:style {:font-size "0.5em"}} "beta"))
     (d/button
      {:class "about-btn"
       :onClick #(set-show-about-dialog true)
       :title "About this application"}
      info-icon))))
 ;; Circled Information Source

(defnc fixed-header [{:keys [children]}]
  (d/div
   {:id "fixed-header"}
   children))

(defnc sticky-wrapper [{:keys [children]}]
  (d/div
   {:class "sticky-wrapper"}
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
    (when (and notification (not (:mark-id notification)))
      (d/div
       {:class (str "notification-toast " (name (:type notification)))}
       (:message notification)))))

(defnc mark-notification-toast [{:keys [mark-id]}]
  (let [{:keys [state dispatch]} (hooks/use-context state/app-context)
        {:keys [notification]} state]
    (hooks/use-effect
     [notification]
     (when (and notification (= (:mark-id notification) mark-id))
       (let [timer (js/setTimeout
                    #(dispatch [:set-notification nil])
                    3000)]
         (fn [] (js/clearTimeout timer)))))
    (when (and notification (= (:mark-id notification) mark-id))
      (d/div
       {:class (str "mark-notification-toast " (name (:type notification)))}
       (:message notification)))))
