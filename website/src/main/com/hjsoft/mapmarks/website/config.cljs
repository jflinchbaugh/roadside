(ns com.hjsoft.mapmarks.website.config)

;; These settings can be configured at compile-time using shadow-cljs :closure-defines
;; or by modifying this file directly.

(goog-define APP_NAME "MapMarks")
(goog-define SITE "mapmarks")
(goog-define APP_DESCRIPTION "Find and share interesting locations")
;; string emoji or image url
(goog-define APP_LOGO "\uD83D\uDCCD") ;; pin emoji
(goog-define DEFAULT_EXPIRATION_DAYS 30)
(goog-define TAGS_NAME_SINGULAR "Tag")
(goog-define TAGS_NAME_PLURAL "Tags")
(goog-define TAGS_NAME_ARTICLE "a")
(goog-define MARK_NAME_SINGULAR "Mark")
(goog-define MARK_NAME_PLURAL "Marks")
(goog-define MARK_NAME_ARTICLE "a")
(goog-define DISABLE_TAGS false)
(goog-define DISABLE_NAME false)

(def config
  {:app-name APP_NAME
   :site SITE
   :app-description APP_DESCRIPTION
   :app-logo APP_LOGO
   :default-expiration-days DEFAULT_EXPIRATION_DAYS
   :tags-name-singular TAGS_NAME_SINGULAR
   :tags-name-plural TAGS_NAME_PLURAL
   :tags-name-article TAGS_NAME_ARTICLE
   :mark-name-singular MARK_NAME_SINGULAR
   :mark-name-plural MARK_NAME_PLURAL
   :mark-name-article MARK_NAME_ARTICLE
   :disable-tags? DISABLE_TAGS
   :disable-name? DISABLE_NAME})
