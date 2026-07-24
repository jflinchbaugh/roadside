(ns com.hjsoft.mapmarks.common.utils
  (:require [clojure.string :as str]
            [com.hjsoft.mapmarks.common.logic :as logic]
            [tick.core :as t]))

(defn get-current-timestamp []
  (str (t/now)))

(defn in-days [d]
  (str (t/>> (t/today) (t/new-period d :days))))

(defn past-expiration? [expiration-str]
  (if (str/blank? expiration-str)
    false
    (let [today (str (t/today))]
      (neg? (compare expiration-str today)))))

(defn random-uuid-str []
  #?(:clj (str (java.util.UUID/randomUUID))
     :cljs (str (cljs.core/random-uuid))))

(def parse-coordinates logic/parse-coordinate)
(def haversine-distance logic/haversine-distance)

(defn get-all-unique-tags [marks]
  (->> marks
       (mapcat :tags)
       (filter string?)
       (map str/trim)
       (map str/lower-case)
       (filter (complement str/blank?))
       distinct
       sort
       vec))
