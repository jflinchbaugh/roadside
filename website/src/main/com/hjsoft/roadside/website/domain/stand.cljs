(ns com.hjsoft.roadside.website.domain.stand
  (:require [com.hjsoft.roadside.website.utils :as utils]
            [clojure.string :as str]))

(defn process-stand-form
  "Processes the stand form data, automatically adding products based on name,
   and returns a map with :success and the updated stands list or an error message."
  [form-data stands editing-stand creator]
  (let [all-unique-products (utils/get-all-unique-products stands)
        stand-name (str/lower-case (:name form-data))
        current-products (set (:products form-data))
        inferred-products (filter
                           (fn [p]
                             (and (str/includes? stand-name (str/lower-case p))
                                  (not (contains? current-products p))))
                           all-unique-products)
        final-products (into (:products form-data) inferred-products)
        processed-data (assoc (if (:id form-data)
                                form-data
                                (assoc form-data
                                       :id (utils/random-uuid-str)
                                       :creator creator))
                              :products (vec final-products)
                              :updated (utils/get-current-timestamp))]
    (if editing-stand
      {:success true
       :processed-data processed-data
       :stands (mapv (fn [s]
                       (if (= (utils/stand-key s) (utils/stand-key editing-stand))
                         processed-data
                         s))
                     stands)}
      (if (some #(= (utils/stand-key processed-data) (utils/stand-key %)) stands)
        {:success false :error "This stand already exists!"}
        {:success true
         :processed-data processed-data
         :stands (conj stands processed-data)}))))
