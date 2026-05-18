(ns com.hjsoft.mapmarks.website.config)

;; These settings can be configured at compile-time using shadow-cljs :closure-defines
;; or by modifying this file directly.

(goog-define APP_NAME "MapMarks")
(goog-define APP_LOGO "\uD83D\uDCCD") ;; 📍
(goog-define DEFAULT_EXPIRATION_DAYS 30)
(goog-define TAGS_NAME_SINGULAR "Tag")
(goog-define TAGS_NAME_PLURAL "Tags")

(def config
  {:app-name APP_NAME
   :app-logo APP_LOGO
   :default-expiration-days DEFAULT_EXPIRATION_DAYS
   :tags-name-singular TAGS_NAME_SINGULAR
   :tags-name-plural TAGS_NAME_PLURAL})
