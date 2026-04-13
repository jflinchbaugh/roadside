(ns com.hjsoft.roadside.website.ui.forms
  (:require [com.hjsoft.roadside.website.ui.forms.stand :as stand]
            [com.hjsoft.roadside.website.ui.forms.settings :as settings]
            [com.hjsoft.roadside.website.ui.forms.export :as export]
            [com.hjsoft.roadside.website.ui.forms.about :as about]
            [com.hjsoft.roadside.website.ui.forms.field :as field]
            [com.hjsoft.roadside.website.ui.forms.inputs :as inputs]))

;; Re-export components for backward compatibility
(def stand-form stand/stand-form)
(def settings-dialog settings/settings-dialog)
(def export-dialog export/export-dialog)
(def about-dialog about/about-dialog)
(def form-field field/form-field)
(def location-input inputs/location-input)
(def product-input inputs/product-input)
