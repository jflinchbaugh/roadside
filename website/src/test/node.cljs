(ns node
  (:require ["global-jsdom" :as global-jsdom]
            [cljs.test :as test]))

;; Initialize JSDOM immediately so that browser globals are available
;; during namespace loading.
(global-jsdom)

;; Mock localStorage if it's not provided by the environment
(when (cljs.core/undefined? js/localStorage)
  (set! js/localStorage #js {:getItem (fn [_] nil)
                             :setItem (fn [_ _] nil)
                             :removeItem (fn [_] nil)}))

(defn main []
  (test/run-all-tests #".*-test$"))
