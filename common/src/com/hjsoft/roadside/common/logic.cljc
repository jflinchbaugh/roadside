(ns com.hjsoft.roadside.common.logic
  (:require [clojure.string :as str]))

(defn parse-coordinate [coord-str]
  (try
    (when (string? coord-str)
      (let [res (->> (str/split coord-str #",\s*")
                     (map str/trim)
                     (map #?(:clj #(Double/parseDouble %)
                             :cljs js/parseFloat))
                     (remove #?(:clj (constantly false) ;; Double/parseDouble throws on failure
                                :cljs js/isNaN)))]
        (when (= 2 (count res)) (vec res))))
    (catch #?(:clj Exception :cljs :default) _ nil)))

(defn deg->rad [deg]
  (* deg (/ #?(:clj Math/PI :cljs (.-PI js/Math)) 180)))

(defn haversine-distance
  "Calculate distance between two points in km."
  [lat1 lon1 lat2 lon2]
  (let [R 6371.0 ; Earth radius in km
        dlat (deg->rad (- lat2 lat1))
        dlon (deg->rad (- lon2 lon1))
        lat1-rad (deg->rad lat1)
        lat2-rad (deg->rad lat2)
        sin-dlat-2 #?(:clj (Math/sin (/ dlat 2)) :cljs (js/Math.sin (/ dlat 2)))
        sin-dlon-2 #?(:clj (Math/sin (/ dlon 2)) :cljs (js/Math.sin (/ dlon 2)))
        a (+ (* sin-dlat-2 sin-dlat-2)
             (* #?(:clj (Math/cos lat1-rad) :cljs (js/Math.cos lat1-rad))
                #?(:clj (Math/cos lat2-rad) :cljs (js/Math.cos lat2-rad))
                sin-dlon-2 sin-dlon-2))
        c (* 2 #?(:clj (Math/atan2 (Math/sqrt a) (Math/sqrt (- 1 a)))
                  :cljs (js/Math.atan2 (js/Math.sqrt a) (js/Math.sqrt (- 1 a)))))]
    (* R c)))

(def UserSchema
  [:map
   [:login [:re #"^[a-zA-Z0-9_]{3,20}$"]]
   [:password [:string {:min 8}]]
   [:email [:re #".+@.+\..+"]]
   [:updated {:optional true} [:maybe :string]]])

(def StandSchema
  [:map
   [:id {:optional true} [:maybe :string]]
   [:creator {:optional true} [:maybe :string]]
   [:name [:string {:min 1}]]
   [:coordinate {:optional true} [:re #"^-?\d+\.?\d*,\s*-?\d+\.?\d*$"]]
   [:address {:optional true} [:maybe :string]]
   [:town {:optional true} [:maybe :string]]
   [:state {:optional true} [:maybe :string]]
   [:products {:optional true} [:vector :string]]
   [:expiration {:optional true} [:maybe :string]]
   [:notes {:optional true} [:maybe :string]]
   [:shared? {:optional true} :boolean]
   [:updated {:optional true} [:maybe :string]]])
