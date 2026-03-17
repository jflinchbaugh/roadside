(ns server.db
  (:require [xtdb.api :as xt]
            [tick.core :as t]
            [com.hjsoft.roadside.common.domain.stand :as common-stand]))

(defonce node (atom nil))

(defn get-user [username]
  (first
   (xt/q @node
         ['(fn [u]
             (-> (from :users [login password email])
                 (where (= login u))))
          username])))

(defn get-stand [id]
  (first
   (xt/q @node
         ['(fn [id-param]
             (-> (from :stands [xt/id *])
                 (where (= xt/id id-param))))
          id])))

(defn list-stands []
  (vec (xt/q @node '(from :stands [*]))))

(defn save-user [user]
  (xt/submit-tx @node [[:put-docs :users (assoc user :updated (str (t/now)))]]))

(defn save-stand [stand]
  (xt/submit-tx @node [[:put-docs :stands (assoc stand :updated (str (t/now)))]]))

(defn delete-stand [id]
  (xt/submit-tx @node [[:delete-docs :stands id]]))
