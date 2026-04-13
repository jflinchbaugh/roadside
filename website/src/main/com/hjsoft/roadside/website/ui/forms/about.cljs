(ns com.hjsoft.roadside.website.ui.forms.about
  (:require [helix.core :refer [defnc $]]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.ui.hooks :as ui-hooks]
            [com.hjsoft.roadside.website.ui.forms.buttons :refer [close-button]]))

(defnc about-dialog []
  (let [{:keys [set-show-about-dialog]} (state/use-ui)]
    (ui-hooks/use-escape-key #(set-show-about-dialog false))
    (d/div
     {:class "dialog-overlay"
      :onClick #(set-show-about-dialog false)}
     (d/div
      {:class "about-dialog"
       :onClick #(.stopPropagation %)}
      (d/div
       {:class "about-header"}
       (d/h3 "About Roadside Stands")
       ($ close-button {:onClick #(set-show-about-dialog false)}))
      (d/div
       {:class "about-content"}
       (d/p
         "Lots of places are too small to be on the big maps,
          but this is your map to add, share, and find
          those small stands along the road
          where you get local produce, crafts, or firewood
          from neighbors.")
       (d/p
         "Create an account (in settings),
          and share stands with the public.
          Place the stand on the map with a list of its products,
          so everyone can search for the things they want.
          Stands automatically expire, because most products are seasonal,
          so pay attention to that expiration date.")
       (d/p
         "Feedback and suggestions are welcome."))))))
