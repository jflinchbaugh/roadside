(ns server.utils
  (:require [clojure.string :as str]))

(defn parse-coordinate [coord-str]
  (try
    (let [[lat lon] (str/split coord-str #",\s*")]
      [(Double/parseDouble lat) (Double/parseDouble lon)])
    (catch Exception _ nil)))

(defn haversine-distance
  "Calculate distance between two points in km."
  [lat1 lon1 lat2 lon2]
  (let [R 6371.0 ; Earth radius in km
        dlat (Math/toRadians (- lat2 lat1))
        dlon (Math/toRadians (- lon2 lon1))
        a (+ (Math/pow (Math/sin (/ dlat 2)) 2)
             (* (Math/cos (Math/toRadians lat1))
                (Math/cos (Math/toRadians lat2))
                (Math/pow (Math/sin (/ dlon 2)) 2)))
        c (* 2 (Math/atan2 (Math/sqrt a) (Math/sqrt (- 1 a))))]
    (* R c)))
