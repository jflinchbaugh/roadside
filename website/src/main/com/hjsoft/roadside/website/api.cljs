(ns com.hjsoft.roadside.website.api
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [go <!]]))

(def ^:private stands-url "api/stands")

(defn- with-auth-opts
  ([user password] (with-auth-opts user password {}))
  ([user password opts]
   (assoc opts :basic-auth {:username user :password password})))

(def default-http-deps
  {:get http/get
   :post http/post
   :put http/put
   :delete http/delete})

(defn fetch-stands
  ([user password]
   (fetch-stands user password nil nil default-http-deps))
  ([user password lat lng]
   (fetch-stands user password lat lng default-http-deps))
  ([user password lat lng {:keys [get]}]
   (go
     (let [params (cond-> {}
                    lat (assoc :lat lat)
                    lng (assoc :lon lng))
           response (<! (get stands-url
                             (with-auth-opts user password {:query-params params})))]
       (if (:success response)
         {:success true
          :data (:body response)}
         {:success false
          :error (str "HTTP Error: " stands-url ", " (:status response))})))))

(defn create-stand
  ([user password stand] (create-stand user password stand default-http-deps))
  ([user password stand {:keys [post]}]
   (go
     (let [response (<! (post stands-url
                              (with-auth-opts user password {:json-params stand})))]
       (if (:success response)
         {:success true :data (:body response)}
         {:success false :error (str "HTTP Error: " (:status response))})))))

(defn update-stand
  ([user password stand] (update-stand user password stand default-http-deps))
  ([user password stand {:keys [put]}]
   (let [id (:id stand)
         resource-url (str stands-url "/" id)]
     (go
       (let [response (<! (put resource-url
                               (with-auth-opts user password {:json-params stand})))]
         (if (:success response)
           {:success true :data (:body response)}
           {:success false :error (str "HTTP Error: " (:status response))}))))))

(defn delete-stand
  ([user password stand-id]
   (delete-stand user password stand-id default-http-deps))
  ([user password stand-id {:keys [delete]}]
   (let [resource-url (str stands-url "/" stand-id)]
     (go
       (let [response (<! (delete resource-url
                                  (with-auth-opts user password)))]
         (if (:success response)
           {:success true}
           {:success false
            :error (str "HTTP Error: " (:status response))}))))))

(defn geocode-address
  ([user password address]
   (geocode-address user password address default-http-deps))
  ([user password address {:keys [get]}]
   (go
     (let [url "api/geocode"
           params {:q address}
           response (<! (get url (with-auth-opts user password {:query-params params})))]
       (if (and (:success response) (seq (:body response)))
         (let [result (first (:body response))]
           {:success true
            :lat (js/parseFloat (:lat result))
            :lng (js/parseFloat (:lon result))})
         {:success false
          :error (or (:status-text response) "Address not found")})))))

(defn reverse-geocode
  ([user password lat lng]
   (reverse-geocode user password lat lng default-http-deps))
  ([user password lat lng {:keys [get]}]
   (go
     (let [url "api/reverse-geocode"
           params {:lat lat :lon lng}
           response (<! (get url (with-auth-opts user password {:query-params params})))]
       (if (:success response)
         {:success true :data (:body response)}
         {:success false
          :error (or (:status-text response) "Location not found")})))))

(defn register-user
  ([user password email]
   (register-user user password email default-http-deps))
  ([user password email {:keys [post]}]
   (go
     (let [url "api/register"
           params {:login user
                   :password password
                   :email email}
           response (<! (post url {:form-params params}))]
       (if (= 201 (:status response))
         {:success true :data (:body response)}
         {:success false
          :error (let [body (:body response)]
                   (if (map? body)
                     (or (:errors body) [(:message body)])
                     [(or (:status-text response)
                          (str "Error: " (:status response)))]))})))))
