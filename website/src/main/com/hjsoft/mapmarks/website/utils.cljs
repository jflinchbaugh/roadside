(ns com.hjsoft.mapmarks.website.utils
  (:require [clojure.string :as str]
            [goog.string]
            [goog.i18n.DateTimeFormat]
            [com.hjsoft.mapmarks.website.init-locale]
            [tick.core :as t]
            ["@js-joda/timezone"]
            [com.hjsoft.mapmarks.common.utils :as common-utils]))

(def get-current-timestamp common-utils/get-current-timestamp)

(defn format-timestamp [iso-str]
  (when (seq iso-str)
    (try
      (let [inst (t/instant iso-str)
            zdt (t/in inst (t/zone))
            formatter (t/formatter "yyyy-MM-dd HH:mm")]
        (t/format formatter zdt))
      (catch :default e
        (.error js/console "FAIL:" (.-stack e) (.-message e) e)
        iso-str))))

(def in-days common-utils/in-days)
(def past-expiration? common-utils/past-expiration?)
(def random-uuid-str common-utils/random-uuid-str)
(def parse-coordinates common-utils/parse-coordinates)

(defn mobile?
  ([] (mobile? (when (exists? js/navigator) js/navigator)))
  ([nav]
   (boolean (and nav (re-find #"(?i)Mobi|Android|iPhone" (.-userAgent nav))))))

(defn make-map-link
  ([lat lon] (make-map-link lat lon (mobile?)))
  ([lat lon is-mobile?]
   (when (and (seq (str lat)) (seq (str lon)))
     (if is-mobile?
       (str "geo:" lat "," lon)
       (str "https://www.google.com/maps/search/?api=1&query=" lat "," lon)))))

(def get-all-unique-tags common-utils/get-all-unique-tags)
(def haversine-distance common-utils/haversine-distance)

(defn mark-popup-html
  "Generates sanitized HTML content for a mark's map popup."
  ([mark] (mark-popup-html mark {}))
  ([mark config]
   (let [name (:name mark)
         tags (:tags mark)
         content (str
                  (when (and (not (:disable-name? config)) (seq name))
                    (str "<b>" (goog.string/htmlEscape name) "</b><br>"))
                  (when (and (not (:disable-tags? config)) (seq tags))
                    (str
                     (str/join ", " (map goog.string/htmlEscape tags))
                     "<br>")))]
     (if (empty? content)
       "(no details)"
       content))))

(defn debounce
  [f ms]
  (let [timer (atom nil)]
    (fn [& args]
      (when @timer (js/clearTimeout @timer))
      (reset! timer (js/setTimeout #(apply f args) ms)))))

(defn copy-to-clipboard! [text]
  (.writeText (.-clipboard js/navigator) text))

(defn get-app-base-url []
  (let [pathname (.. js/window -location -pathname)
        ;; Assuming index.html is at the base
        base (if (str/ends-with? pathname "/")
               pathname
               (str (str/join "/" (butlast (str/split pathname #"/"))) "/"))]
    (str (.. js/window -location -origin) base)))
