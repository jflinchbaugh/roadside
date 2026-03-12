(ns com.hjsoft.roadside.website.utils
  (:require [clojure.string :as str]
            [goog.string]))

(defn get-current-timestamp []
  (.toISOString (js/Date.)))

(defn in-a-week []
  (let [date (js/Date.)
        week-later (+ (.getTime date) (* 7 24 60 60 1000))]
    (.substring (.toISOString (js/Date. week-later)) 0 10)))

(defn past-expiration? [expiration-str]
  (if (str/blank? expiration-str)
    false
    (let [today (.substring (.toISOString (js/Date.)) 0 10)]
      (neg? (compare expiration-str today)))))

(defn show-system-notification
  "Displays a browser system notification if permissions are granted."
  [title options]
  (when (exists? js/Notification)
    (case js/Notification.permission
      "granted" (js/Notification. title (clj->js options))
      "denied" nil
      (.requestPermission
       js/Notification
       (fn [permission]
         (when (= permission "granted")
           (js/Notification. title (clj->js options))))))))

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
