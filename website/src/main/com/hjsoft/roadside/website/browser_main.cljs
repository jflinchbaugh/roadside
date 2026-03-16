(ns com.hjsoft.roadside.website.browser-main
  (:require [com.hjsoft.roadside.website.core :as core]
            [com.hjsoft.roadside.website.ui.map :as ui-map]
            ["leaflet" :as L]))

(defn init []
  (ui-map/set-leaflet! L)
  (core/init))
