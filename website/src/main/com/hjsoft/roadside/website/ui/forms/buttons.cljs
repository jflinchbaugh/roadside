(ns com.hjsoft.roadside.website.ui.forms.buttons
  (:require [helix.core :refer [defnc $]]
            [helix.dom :as d]))

(def x-icon "\u2715")

(defnc close-button [{:keys [onClick title class-name] :or {title "Close"}}]
  (d/button
   {:type "button"
    :class (str "button icon-button " class-name)
    :onClick onClick
    :title title}
   x-icon))
