(ns com.hjsoft.roadside.website.ui.forms.field
  (:require [helix.core :refer [defnc]]
            [helix.dom :as d]))

(defnc form-field
  [{:keys [label type value on-change on-blur rows
           checked id class-name placeholder]
    :or {type "text"
         value ""}}]
  (d/div
   {:class "form-group"}
   (when label
     (d/label {:htmlFor id} label))
   (if (= type "textarea")
     (d/textarea
      {:value value
       :onChange on-change
       :onBlur on-blur
       :rows (or rows 3)
       :placeholder placeholder})
     (d/input {:id id
               :value value
               :checked checked
               :type type
               :onChange on-change
               :onBlur on-blur
               :class class-name
               :placeholder placeholder}))))
