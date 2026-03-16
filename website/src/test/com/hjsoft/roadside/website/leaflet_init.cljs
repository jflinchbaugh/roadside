(ns com.hjsoft.roadside.website.leaflet-init
  (:require [node-init]
            ["leaflet" :as L]
            [com.hjsoft.roadside.website.ui.map :as ui-map]))

(ui-map/set-leaflet! L)
