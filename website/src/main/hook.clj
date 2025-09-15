(ns hook
  (:require [clojure.string :as str]))

(defn version-index-resources
  {:shadow.build/stage :flush}
  [state]
  (let [ts (str (System/currentTimeMillis))]
    (spit "public/index.html" (str/replace (slurp "src/html/index.html") "{ts}" ts)))
  state)
