(ns com.hjsoft.mapmarks.website.leaflet-init
  (:require [node-init]
            ["leaflet" :as L]
            [com.hjsoft.mapmarks.website.ui.map :as ui-map]))

(ui-map/set-leaflet! L)
