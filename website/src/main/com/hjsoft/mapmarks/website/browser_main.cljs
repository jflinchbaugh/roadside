(ns com.hjsoft.mapmarks.website.browser-main
  (:require [com.hjsoft.mapmarks.website.core :as core]
            [com.hjsoft.mapmarks.website.ui.map :as ui-map]
            ["leaflet" :as L]))

(defn init []
  (ui-map/set-leaflet! L)
  (core/init))
