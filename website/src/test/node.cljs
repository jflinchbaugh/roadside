(ns node
  (:require [init]
            [cljs.test :as test]
            [com.hjsoft.roadside.website.utils-test]
            [com.hjsoft.roadside.website.state-test]
            [com.hjsoft.roadside.website.ui.layout-test]))

(defn main []
  (test/run-all-tests #".*-test$"))