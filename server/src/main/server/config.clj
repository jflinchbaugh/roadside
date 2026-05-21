(ns server.config)

(def base-url "/mapmarks")

(def site (or (System/getenv "SITE") "mapmarks"))

(def external-base-url (or (System/getenv "EXTERNAL_BASE_URL")
                           (str "http://localhost:3000" base-url "/")))
