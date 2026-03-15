(ns com.hjsoft.roadside.website.utils
  (:require [clojure.string :as str]
            [goog.string]
            [goog.i18n.DateTimeFormat]))

(defn get-current-timestamp []
  (.toISOString (js/Date.)))

(defn format-timestamp [iso-str]
  (when (seq iso-str)
    (let [date (js/Date. iso-str)]
      (if (js/isNaN (.getTime date))
        iso-str
        (let [formatter (goog.i18n.DateTimeFormat. "yyyy-MM-dd HH:mm")]
          (.format formatter date))))))

(defn in-a-week []
  (let [date (js/Date.)
        week-later (+ (.getTime date) (* 7 24 60 60 1000))]
    (.substring (.toISOString (js/Date. week-later)) 0 10)))

(defn past-expiration? [expiration-str]
  (if (str/blank? expiration-str)
    false
    (let [today (.substring (.toISOString (js/Date.)) 0 10)]
      (neg? (compare expiration-str today)))))

(defn random-uuid-str []
  (str (cljs.core/random-uuid)))

(defn parse-coordinates
  [coords]
  (when (string? coords)
    (let [res (->>
               (str/split coords #", *")
               (map str/trim)
               (map parse-double)
               (remove nil?))]
      (when (= 2 (count res)) res))))

(defn make-map-link [coordinate-str]
  (when coordinate-str
    (let [[lat lng] (str/split coordinate-str #", *")]
      (when (and lat lng)
        (str "geo:" (str/trim lat) "," (str/trim lng))))))

(defn get-all-unique-products [stands]
  (->> stands
       (mapcat :products)
       (filter string?)
       (map str/trim)
       (filter (complement str/blank?))
       distinct
       sort
       vec))

(defn haversine-distance
  "Calculate distance between two points in km."
  [lat1 lon1 lat2 lon2]
  (let [R 6371.0 ; Earth radius in km
        to-rad (fn [deg] (* deg (/ (.-PI js/Math) 180)))
        dlat (to-rad (- lat2 lat1))
        dlon (to-rad (- lon2 lon1))
        a (+ (js/Math.pow (js/Math.sin (/ dlat 2)) 2)
             (* (js/Math.cos (to-rad lat1))
                (js/Math.cos (to-rad lat2))
                (js/Math.pow (js/Math.sin (/ dlon 2)) 2)))
        c (* 2 (js/Math.atan2 (js/Math.sqrt a) (js/Math.sqrt (- 1 a))))]
    (* R c)))

(defn stand-popup-html
  "Generates sanitized HTML content for a stand's map popup."
  [stand]
  (let [name (:name stand)
        products (:products stand)
        content (str
                 (when (seq name)
                   (str "<b>" (goog.string/htmlEscape name) "</b><br>"))
                 (when (seq products)
                   (str
                    (str/join ", " (map goog.string/htmlEscape products))
                    "<br>")))]
    (if (empty? content)
      "(no details)"
      content)))

(defn debounce
  [f ms]
  (let [timer (atom nil)]
    (fn [& args]
      (when @timer (js/clearTimeout @timer))
      (reset! timer (js/setTimeout #(apply f args) ms)))))
