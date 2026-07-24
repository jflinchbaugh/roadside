(ns com.hjsoft.mapmarks.website.init-locale
  (:require ["@js-joda/locale" :as js-joda-locale]
            ["@js-joda/locale_en-us"]))

(set! (.-JSJodaLocale js/goog.global) js-joda-locale)
