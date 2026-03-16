(ns server.geocoding
  (:require [org.httpkit.client :as hkc]
            [clojure.data.json :as json]))

(def ^:const nominatim-search-url "https://nominatim.openstreetmap.org/search")
(def ^:const nominatim-reverse-url "https://nominatim.openstreetmap.org/reverse")

(defn geocode [address]
  (let [query-params {:q address :format "json" :limit 1}
        {:keys [status body error]} @(hkc/get nominatim-search-url
                                               {:query-params query-params
                                                :headers {"User-Agent" "RoadsideStandsApp/1.0"}})]
    (if (or error (not= status 200))
      {:error (str "Nominatim error: " (or error status))}
      {:data (json/read-str body :key-fn keyword)})))

(defn reverse-geocode [lat lon]
  (let [query-params {:lat lat :lon lon :format "json"}
        {:keys [status body error]} @(hkc/get nominatim-reverse-url
                                               {:query-params query-params
                                                :headers {"User-Agent" "RoadsideStandsApp/1.0"}})]
    (if (or error (not= status 200))
      {:error (str "Nominatim error: " (or error status))}
      {:data (json/read-str body :key-fn keyword)})))
