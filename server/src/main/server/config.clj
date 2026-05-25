(ns server.config)

(defonce env (fn [var-name] (System/getenv var-name)))

(def base-url (or (env "BASE_URL") "/mapmarks"))

(def site (or (env "SITE") "mapmarks"))

(def default-site-config
  {:app-name "MapMarks Marks"
   :app-description "Latest MapMarks Marks"
   :mark-name-singular "Mark"
   :mark-name-plural "Marks"
   :tags-name-singular "Tag"
   :tags-name-plural "Tags"
   :external-base-url (str "http://localhost:3000" base-url "/")})

(def site-configs
  {"mapmarks" default-site-config
   "roadside" {:app-name "Roadside Stands"
               :app-description "Latest Roadside Stands"
               :mark-name-singular "Stand"
               :mark-name-plural "Stands"
               :tags-name-singular "Product"
               :tags-name-plural "Products"
               :external-base-url "https://www.hjsoft.com/roadside/"}})

(defn get-config [site]
  (get site-configs site default-site-config))
