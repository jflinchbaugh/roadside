(ns com.hjsoft.roadside.website.api
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [go <!]]))

(def ^:private stands-url "api/stands")

(defn fetch-stands [user password]
  (go
    (let [response (<! (http/get stands-url
                                 {:basic-auth {:username user :password password}}))]
      (if (:success response)
        {:success true :data (:body response)}
        {:success false :error (str "HTTP Error: " stands-url ", " (:status response))}))))

(defn create-stand [user password stand]
  (go
    (let [response (<! (http/post stands-url
                                  {:basic-auth {:username user :password password}
                                   :json-params stand}))]
      (if (:success response)
        {:success true :data (:body response)}
        {:success false :error (str "HTTP Error: " (:status response))}))))

(defn update-stand [user password stand]
  (let [id (:id stand)
        resource-url (str stands-url "/" id)]
    (go
      (let [response (<! (http/put resource-url
                                   {:basic-auth {:username user :password password}
                                    :json-params stand}))]
        (if (:success response)
          {:success true :data (:body response)}
          {:success false :error (str "HTTP Error: " (:status response))})))))

(defn delete-stand [user password stand-id]
  (let [resource-url (str stands-url "/" stand-id)]
    (go
      (let [response (<! (http/delete resource-url
                                      {:basic-auth {:username user :password password}}))]
        (if (:success response)
          {:success true}
          {:success false :error (str "HTTP Error: " (:status response))})))))

(defn geocode-address [user password address]
  (go
    (let [url "api/geocode"
          params {:q address}
          response (<! (http/get url {:query-params params
                                      :basic-auth {:username user :password password}}))]
      (if (and (:success response) (seq (:body response)))
        (let [result (first (:body response))]
          {:success true
           :lat (js/parseFloat (:lat result))
           :lng (js/parseFloat (:lon result))})
        {:success false :error (or (:status-text response) "Address not found")}))))

(defn reverse-geocode [user password lat lng]
  (go
    (let [url "api/reverse-geocode"
          params {:lat lat :lon lng}
          response (<! (http/get url {:query-params params
                                      :basic-auth {:username user :password password}}))]
      (if (:success response)
        {:success true :data (:body response)}
        {:success false :error (or (:status-text response) "Location not found")}))))

(defn register-user [user password email]
  (go
    (let [url "api/register"
          params {:login user
                  :password password
                  :email email}
          response (<! (http/post url {:query-params params}))]
      (if (= 201 (:status response))
        {:success true :data (:body response)}
        {:success false :error (or (get-in response [:body :message])
                                   (str "Error: " (:status response)))}))))

