(ns com.hjsoft.mapmarks.website.build-hooks
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(defn- replace-placeholders [content replacements]
  (reduce (fn [c [k v]]
            (str/replace c (str "{" k "}") (str v)))
          content
          replacements))

(defn version-index-resources
  {:shadow.build/stage :flush}
  [state]
  (let [ts (str (System/currentTimeMillis))
        closure-defines (get-in state [:shadow.build/config :closure-defines])
        app-name (get closure-defines 'com.hjsoft.mapmarks.website.config/APP_NAME "MapMarks")
        app-description (get closure-defines 'com.hjsoft.mapmarks.website.config/APP_DESCRIPTION "Find and share interesting locations")
        app-icon (get closure-defines
                      'com.hjsoft.mapmarks.website.config/APP_ICON
                      "favicon.ico")
        mark-name-singular (get closure-defines 'com.hjsoft.mapmarks.website.config/MARK_NAME_SINGULAR "Mark")
        site (get closure-defines 'com.hjsoft.mapmarks.website.config/SITE "mapmarks")
        replacements {"ts" ts
                      "app-name" app-name
                      "app-description" app-description
                      "app-icon" app-icon
                      "mark-name-singular" mark-name-singular
                      "mark-name-singular-lc" (str/lower-case (str mark-name-singular))
                      "site" site}]
    (spit "public/index.html" (replace-placeholders (slurp "src/html/index.html") replacements))
    (spit "public/manifest.json" (replace-placeholders (slurp "src/html/manifest.json") replacements)))
  state)

(defn version-cljs
  {:shadow.build/stage :configure}
  [state]
  (let [ts (str (java.time.Instant/now))
        content (str "(ns com.hjsoft.mapmarks.website.version)\n\n"
                     "(def build-date \"" ts "\")\n")]
    (io/make-parents "src/main/com/hjsoft/mapmarks/website/version.cljs")
    (spit "src/main/com/hjsoft/mapmarks/website/version.cljs" content))
  state)
