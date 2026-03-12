(ns init)

;; Mock Notification for testing
(def notification-calls (atom []))

(set! js/Notification
      (fn [title options]
        (swap! notification-calls conj {:title title :options (js->clj options :keywordize-keys true)})
        #js {}))

(set! (.-permission js/Notification) "granted")
(set! (.-requestPermission js/Notification)
      (fn [callback]
        (callback "granted")))
