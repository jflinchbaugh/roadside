(ns node-init)

(goog-define ENABLE_JSDOM false)

(when ENABLE_JSDOM
  (let [lib "global-jsdom"
        global-jsdom (js/require lib)]
    (global-jsdom)))
