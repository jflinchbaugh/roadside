(ns com.hjsoft.roadside.common.utils
  (:require [clojure.string :as str]
            [com.hjsoft.roadside.common.logic :as logic]))

(defn get-current-timestamp []
  #?(:clj (.format (java.time.format.DateTimeFormatter/ISO_INSTANT) (java.time.Instant/now))
     :cljs (.toISOString (js/Date.))))

(defn in-days [d]
  #?(:clj (str (.plus (java.time.LocalDate/now) d java.time.temporal.ChronoUnit/DAYS))
     :cljs (.substring (.toISOString (js/Date. (+ (.getTime (js/Date.)) (* d 24 60 60 1000)))) 0 10)))

(defn past-expiration? [expiration-str]
  (if (str/blank? expiration-str)
    false
    (let [today #?(:clj (str (java.time.LocalDate/now))
                   :cljs (.substring (.toISOString (js/Date.)) 0 10))]
      (neg? (compare expiration-str today)))))

(defn random-uuid-str []
  #?(:clj (str (java.util.UUID/randomUUID))
     :cljs (str (cljs.core/random-uuid))))

(def parse-coordinates logic/parse-coordinate)
(def haversine-distance logic/haversine-distance)

(defn get-all-unique-products [stands]
  (->> stands
       (mapcat :products)
       (filter string?)
       (map str/trim)
       (filter (complement str/blank?))
       distinct
       sort
       vec))
