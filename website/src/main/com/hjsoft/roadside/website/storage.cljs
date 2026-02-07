(ns com.hjsoft.roadside.website.storage
  (:require [cljs.reader]
            [clojure.edn :as edn]))

(defn set-item!
  "Serializes value using pr-str and saves it to localStorage."
  [key value]
  (when (exists? js/localStorage)
    (.setItem js/localStorage key (pr-str value))))

(defn get-item
  "Retrieves value from localStorage and parses it using edn/read-string."
  [key]
  (when (exists? js/localStorage)
    (when-let [item (.getItem js/localStorage key)]
      (edn/read-string item))))

(defn remove-item!
  "Removes item from localStorage."
  [key]
  (when (exists? js/localStorage)
    (.removeItem js/localStorage key)))
