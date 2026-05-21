(ns server.config)

(def base-url (or (System/getenv "BASE_URL") "/mapmarks"))

(def site (or (System/getenv "SITE") "mapmarks"))

(def external-base-url (or (System/getenv "EXTERNAL_BASE_URL")
                           (str "http://localhost:3000" base-url "/")))

(def default-site-config
  {:app-name (or (System/getenv "APP_NAME") "MapMarks Marks")
   :app-description (or (System/getenv "APP_DESCRIPTION")
                        "Latest MapMarks Marks")
   :mark-name-singular (or (System/getenv "MARK_NAME_SINGULAR") "Mark")
   :mark-name-plural (or (System/getenv "MARK_NAME_PLURAL") "Marks")
   :tags-name-singular (or (System/getenv "TAGS_NAME_SINGULAR") "Tag")
   :tags-name-plural (or (System/getenv "TAGS_NAME_PLURAL") "Tags")})

(def site-configs
  {"mapmarks" default-site-config})

(defn get-config [site]
  (get site-configs site default-site-config))

