(ns server.utils
  (:require [com.hjsoft.mapmarks.common.utils :as common-utils]))

(def parse-coordinate common-utils/parse-coordinates)
(def haversine-distance common-utils/haversine-distance)
(def get-current-timestamp common-utils/get-current-timestamp)
