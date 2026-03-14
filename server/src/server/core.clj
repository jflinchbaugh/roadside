(ns server.core
  (:gen-class)
  (:require [org.httpkit.server :as hks]
            [org.httpkit.client :as hkc]
            [reitit.ring :as ring]
            [ring.middleware.defaults :as rmd]
            [ring.middleware.cors :as rmc]
            [ring.util.request :as rur]
            [buddy.auth.middleware :as buddy]
            [buddy.auth.backends :as backends]
            [buddy.hashers :as hashers]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [tick.core :as t]
            [xtdb.api :as xt]
            [taoensso.telemere :as tel]))

(defonce node (atom nil))

(def ^:const realm "roadside")

(def ^:const base-url "/roadside")

(defn api-response
  [code document]
  {:status code
   :headers {"Content-Type" "application/json"}
   :body (json/write-str document)})

(defn not-found
  [& _]
  (api-response
   404
   {:error "Not Found"}))

(defn unauthorized
  [& _]
  {:status 401
   :headers {"Content-Type" "application/json"
             "WWW-Authenticate" (format "Basic realm=%s" realm)}
   :body (json/write-str {:error "Unauthorized"})})

(defn ping-handler
  [_]
  (api-response 200 "pong"))

(defn geocode-handler
  [req]
  (let [address (get-in req [:params :q])]
    (if (str/blank? address)
      (api-response 400 {:error "Missing address"})
      (let [url "https://nominatim.openstreetmap.org/search"
            query-params {:q address
                          :format "json"
                          :limit 1}
            {:keys [status body error]} @(hkc/get url {:query-params query-params
                                                       :headers {"User-Agent" "RoadsideStandsApp/1.0"}})]
        (if (or error (not= status 200))
          (api-response 502 {:error (str "Nominatim error: " (or error status))})
          (api-response 200 (json/read-str body :key-fn keyword)))))))

(defn reverse-geocode-handler
  [req]
  (let [lat (get-in req [:params :lat])
        lon (get-in req [:params :lon])]
    (if (or (str/blank? lat) (str/blank? lon))
      (api-response 400 {:error "Missing lat or lon"})
      (let [url "https://nominatim.openstreetmap.org/reverse"
            query-params {:lat lat
                          :lon lon
                          :format "json"}
            {:keys [status body error]} @(hkc/get url {:query-params query-params
                                                       :headers {"User-Agent" "RoadsideStandsApp/1.0"}})]
        (if (or error (not= status 200))
          (api-response 502 {:error (str "Nominatim error: " (or error status))})
          (api-response 200 (json/read-str body :key-fn keyword)))))))

(defn register-handler
  [req]
  (let [id (or (get-in req [:params :id]) (str (java.util.UUID/randomUUID)))
        login (get-in req [:params :login])
        password (get-in req [:params :password])
        email (get-in req [:params :email])
        missing-fields (cond-> []
                         (str/blank? email) (conj "email")
                         (str/blank? login) (conj "login")
                         (str/blank? password) (conj "password"))
        invalid-fields (cond-> []
                         (and (not (str/blank? email))
                              (not (re-matches #".+@.+\..+" email)))
                         (conj "invalid email format")

                         (and (not (str/blank? login))
                              (not (re-matches #"^[a-zA-Z0-9_]{3,20}$" login)))
                         (conj "login must be 3-20 alphanumeric characters")

                         (and (not (str/blank? password))
                              (< (count password) 8))
                         (conj "password must be at least 8 characters"))
        user {:xt/id id
              :login login
              :password (hashers/derive password)
              :email email
              :updated (str (t/now))}
        existing-user (first
                       (xt/q @node
                             ['(fn [u]
                                 (->
                                  (from :users [login])
                                  (where (= login u))))
                              login]))]
    (cond
      (seq missing-fields)
      (api-response 400 {:status "failed"
                         :errors (mapv #(str % " is required")
                                       missing-fields)})

      (seq invalid-fields)
      (api-response 400 {:status "failed"
                         :errors invalid-fields})

      existing-user
      (api-response 403 {:status "failed" :errors ["login not available"]})

      :else
      (do
        (xt/submit-tx @node [[:put-docs :users user]])
        (api-response 201 {:login login})))))

(defn get-stands-handler
  [_req]
  (let [stands (vec (xt/q @node '(from :stands [*])))]
    (api-response 200 stands)))

(defn get-stand-handler
  [req]
  (let [id (get-in req [:path-params :id])
        stand (first
               (xt/q @node
                     ['(fn [id-param]
                         (->
                          (from :stands [xt/id *])
                          (where (= xt/id id-param))))
                      id]))]
    (if stand
      (api-response 200 stand)
      (not-found))))

(defn create-stand-handler
  [req]
  (let [stand (-> (json/read-str (rur/body-string req) :key-fn keyword)
                  (dissoc :creator))
        stand (assoc stand :xt/id (or (:id stand) (:xt/id stand) (str (java.util.UUID/randomUUID)))
                     :updated (str (t/now))
                     :creator (:identity req))
        stand (dissoc stand :id)]
    (xt/submit-tx @node [[:put-docs :stands stand]])
    (api-response 201 stand)))

(defn update-stand-handler
  [req]
  (let [id (get-in req [:path-params :id])
        stand (-> (json/read-str (rur/body-string req) :key-fn keyword)
                  (dissoc :creator))
        existing-stand (first
                        (xt/q @node
                              ['(fn [id-param]
                                  (->
                                   (from :stands [xt/id *])
                                   (where (= xt/id id-param))))
                               id]))]
    (if (and existing-stand (not= (:creator existing-stand) (:identity req)))
      (api-response 403 {:error "Forbidden: You do not own this stand"})
      (let [stand (assoc stand :xt/id id
                         :updated (str (t/now))
                         :creator (or (:creator existing-stand) (:identity req)))
            stand (dissoc stand :id)]
        (xt/submit-tx @node [[:put-docs :stands stand]])
        (api-response 200 stand)))))

(defn delete-stand-handler
  [req]
  (let [id (get-in req [:path-params :id])
        existing-stand (first
                        (xt/q @node
                              ['(fn [id-param]
                                  (->
                                   (from :stands [xt/id *])
                                   (where (= xt/id id-param))))
                               id]))]
    (if (and existing-stand (not= (:creator existing-stand) (:identity req)))
      (api-response 403 {:error "Forbidden: You do not own this stand"})
      (do
        (xt/submit-tx @node [[:delete-docs :stands id]])
        (api-response 200 {:message (format "'%s' deleted" id)})))))

(defn identity-required-wrapper
  [handler]
  (fn [req]
    (if (nil? (:identity req))
      (unauthorized)
      (handler req))))

(defn my-authfn
  [_req authdata]
  (let [username (:username authdata)
        password (:password authdata)
        user (first
              (xt/q @node
                    ['(fn [u]
                        (-> (from :users [login password])
                            (where (= login u))))
                     username]))]
    (when (and user (:valid (hashers/verify password (:password user))))
      (:login user))))

(def backend (backends/basic {:realm realm :authfn my-authfn}))

(defn authenticated-for-logger [handler]
  (buddy/wrap-authentication handler backend))

(def app
  (-> [base-url
       ["/api"
        ["/ping" ping-handler]
        ["/geocode" {:middleware [authenticated-for-logger identity-required-wrapper]
                     :get geocode-handler}]
        ["/reverse-geocode" {:middleware [authenticated-for-logger identity-required-wrapper]
                             :get reverse-geocode-handler}]
        ["/register" {:post register-handler}]
        ["/stands" {:middleware
                    [authenticated-for-logger identity-required-wrapper]
                    :get get-stands-handler
                    :post create-stand-handler}]
        ["/stands/:id" {:middleware
                        [authenticated-for-logger identity-required-wrapper]
                        :get get-stand-handler
                        :put update-stand-handler
                        :delete delete-stand-handler}]]]
      (ring/router)
      (ring/ring-handler
       (ring/routes
        (ring/create-resource-handler {:path base-url})
        not-found))
      (rmc/wrap-cors
       :access-control-allow-origin [#".*"]
       :access-control-allow-methods [:get :put :post :delete])
      (rmd/wrap-defaults
       (assoc
        rmd/api-defaults
        :proxy true))))

(defonce server (atom nil))

(defn stop-server!
  []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil))
  (reset! node nil))

(defn start-server!
  [port db-host]
  (if (nil? @server)
    (do
      (reset! node (xt/client {:host db-host}))
      (reset! server (hks/run-server #'app {:port port}))
      (tel/log! :info "Server started."))
    "server already running"))

(defn -main [& [port db-host]]
  (if (or (nil? port) (nil? db-host))
    (println "Usage: <port> <xtdb-url>")
    (start-server! (Integer/parseInt port) db-host)))

(comment

  (start-server! 8080 "localhost")

  (stop-server!)

  (app {:scheme :http :request-method :get :uri "/roadside/api/ping"})

  (let [node (xt/client {:host "localhost"})]
    (xt/q node '(from :roadside [*])))

  nil)

