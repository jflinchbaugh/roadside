(ns com.hjsoft.roadside.website.utils
  (:require [clojure.string :as str]
            [goog.string]
            [goog.i18n.DateTimeFormat]
            [com.hjsoft.roadside.common.utils :as common-utils]))

(def get-current-timestamp common-utils/get-current-timestamp)

(defn format-timestamp [iso-str]
  (when (seq iso-str)
    (let [date (js/Date. iso-str)]
      (if (js/isNaN (.getTime date))
        iso-str
        (let [formatter (goog.i18n.DateTimeFormat. "yyyy-MM-dd HH:mm")]
          (.format formatter date))))))

(def in-a-week common-utils/in-a-week)
(def past-expiration? common-utils/past-expiration?)
(def random-uuid-str common-utils/random-uuid-str)
(def parse-coordinates common-utils/parse-coordinates)

(defn make-map-link [coordinate-str]
  (when coordinate-str
    (let [[lat lng] (str/split coordinate-str #", *")]
      (when (and lat lng)
        (str "geo:" (str/trim lat) "," (str/trim lng))))))

(def get-all-unique-products common-utils/get-all-unique-products)
(def haversine-distance common-utils/haversine-distance)

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
