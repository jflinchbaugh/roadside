(ns server.config)

(def base-url (or (System/getenv "BASE_URL") "/mapmarks"))

(def site (or (System/getenv "SITE") "mapmarks"))

(def external-base-url (or (System/getenv "EXTERNAL_BASE_URL")
                           (str "http://localhost:3000" base-url "/")))
