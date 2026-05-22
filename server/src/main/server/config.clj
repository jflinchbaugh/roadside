(ns server.config)

(defonce env (fn [var-name] (System/getenv var-name)))

(def base-url (or (env "BASE_URL") "/mapmarks"))

(def site (or (env "SITE") "mapmarks"))

(def external-base-url (or (env "EXTERNAL_BASE_URL")
                           (str "http://localhost:3000" base-url "/")))

(def default-site-config
  {:app-name (or (env "APP_NAME") "MapMarks Marks")
   :app-description (or (env "APP_DESCRIPTION")
                        "Latest MapMarks Marks")
   :mark-name-singular (or (env "MARK_NAME_SINGULAR") "Mark")
   :mark-name-plural (or (env "MARK_NAME_PLURAL") "Marks")
   :tags-name-singular (or (env "TAGS_NAME_SINGULAR") "Tag")
   :tags-name-plural (or (env "TAGS_NAME_PLURAL") "Tags")})

(def site-configs
  {"mapmarks" default-site-config})

(defn get-config [site]
  (get site-configs site default-site-config))
