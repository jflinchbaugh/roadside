(ns node
  (:require [node-init]
            [com.hjsoft.roadside.website.leaflet-init]
            [init]
            [cljs.test :as test]
            [com.hjsoft.roadside.website.utils-test]
            [com.hjsoft.roadside.website.state-test]
            [com.hjsoft.roadside.website.ui.layout-test]
            [com.hjsoft.roadside.website.ui.hooks-test]
            [com.hjsoft.roadside.website.ui.map-test]
            [com.hjsoft.roadside.website.ui.forms-test]
            [com.hjsoft.roadside.website.ui.stands-test]
            [com.hjsoft.roadside.website.controller-test]
            [com.hjsoft.roadside.website.api-test]
            [com.hjsoft.roadside.website.storage-test]
            [com.hjsoft.roadside.website.domain.stand-test]))

(defn main []
  (test/run-all-tests #".*-test$"))
