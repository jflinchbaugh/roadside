(ns node-init)

(goog-define ENABLE_JSDOM false)

(when ENABLE_JSDOM
  (let [lib "global-jsdom"
        global-jsdom (js/require lib)]
    (global-jsdom)
    ;; Silence React 18/19/JSDOM noise: activeElement.attachEvent/detachEvent
    (when (exists? js/Element)
      (set! (.. js/Element -prototype -attachEvent) (fn []))
      (set! (.. js/Element -prototype -detachEvent) (fn [])))))
