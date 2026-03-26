(ns node-init)

(goog-define ENABLE_JSDOM false)

(when ENABLE_JSDOM
  (let [lib "global-jsdom"
        global-jsdom (js/require lib)]
    (global-jsdom)
    ;; Silence React 18/JSDOM noise: activeElement.attachEvent is not a function
    (when (exists? js/Element)
      (set! (.. js/Element -prototype -attachEvent) (fn [])))))
