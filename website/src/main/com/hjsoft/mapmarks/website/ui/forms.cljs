(ns com.hjsoft.mapmarks.website.ui.forms
  (:require [com.hjsoft.mapmarks.website.ui.forms.mark :as mark]
            [com.hjsoft.mapmarks.website.ui.forms.settings :as settings]
            [com.hjsoft.mapmarks.website.ui.forms.export :as export]
            [com.hjsoft.mapmarks.website.ui.forms.about :as about]
            [com.hjsoft.mapmarks.website.ui.forms.field :as field]
            [com.hjsoft.mapmarks.website.ui.forms.inputs :as inputs]
            [com.hjsoft.mapmarks.website.ui.forms.buttons :as buttons]))

;; Re-export components for backward compatibility
(def mark-form mark/mark-form)
(def settings-dialog settings/settings-dialog)
(def export-dialog export/export-dialog)
(def about-dialog about/about-dialog)
(def form-field field/form-field)
(def location-input inputs/location-input)
(def tag-input inputs/tag-input)
(def close-button buttons/close-button)
