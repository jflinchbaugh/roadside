(ns node-init)

(goog-define ENABLE_JSDOM false)

(when ENABLE_JSDOM
  (let [global-jsdom (js/require "global-jsdom")]
    (global-jsdom)))
