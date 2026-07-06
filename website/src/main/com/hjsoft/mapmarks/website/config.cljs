(ns com.hjsoft.mapmarks.website.config)

;; These settings must be configured at compile-time
;; using shadow-cljs :closure-defines

(goog-define APP_NAME "Roadside Stands")
(goog-define SITE "roadside")
(goog-define APP_DESCRIPTION "Find and share roadside produce stands")
;; string emoji or image url
(goog-define APP_LOGO "images/apples.png")
(goog-define APP_ICON "images/apples.png")
(goog-define DEFAULT_EXPIRATION_DAYS 28)
(goog-define TAGS_NAME_SINGULAR "Product")
(goog-define TAGS_NAME_PLURAL "Products")
(goog-define TAGS_NAME_ARTICLE "a")
(goog-define MARK_NAME_ARTICLE "a")
(goog-define MARK_NAME_SINGULAR "Mark")
(goog-define MARK_NAME_PLURAL "Marks")
(goog-define DISABLE_TAGS false)
(goog-define DISABLE_NAME false)
(goog-define FEEDBACK_EMAIL "mapmarks@hjsoft.com")

(def config
  {:app-name APP_NAME
   :site SITE
   :app-description APP_DESCRIPTION
   :app-logo APP_LOGO
   :app-icon APP_ICON
   :default-expiration-days DEFAULT_EXPIRATION_DAYS
   :tags-name-singular TAGS_NAME_SINGULAR
   :tags-name-plural TAGS_NAME_PLURAL
   :tags-name-article TAGS_NAME_ARTICLE
   :mark-name-singular MARK_NAME_SINGULAR
   :mark-name-plural MARK_NAME_PLURAL
   :mark-name-article MARK_NAME_ARTICLE
   :disable-tags? DISABLE_TAGS
   :disable-name? DISABLE_NAME
   :feedback-email FEEDBACK_EMAIL})

