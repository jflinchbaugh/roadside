(ns node
  (:require [node-init]
            [com.hjsoft.mapmarks.website.leaflet-init]
            [init]
            [cljs.test :as test]
            [com.hjsoft.mapmarks.website.utils-test]
            [com.hjsoft.mapmarks.website.state-test]
            [com.hjsoft.mapmarks.website.core-test]
            [com.hjsoft.mapmarks.website.ui.layout-test]
            [com.hjsoft.mapmarks.website.ui.hooks-test]
            [com.hjsoft.mapmarks.website.ui.map-test]
            [com.hjsoft.mapmarks.website.ui.forms-test]
            [com.hjsoft.mapmarks.website.ui.about-test]
            [com.hjsoft.mapmarks.website.ui.settings-test]
            [com.hjsoft.mapmarks.website.ui.marks-test]
            [com.hjsoft.mapmarks.website.controller-test]
            [com.hjsoft.mapmarks.website.local-only-test]
            [com.hjsoft.mapmarks.website.api-test]
            [com.hjsoft.mapmarks.website.storage-test]
            [com.hjsoft.mapmarks.website.domain.mark-test]))

(defn main []
  (test/run-all-tests #".*-test$"))
