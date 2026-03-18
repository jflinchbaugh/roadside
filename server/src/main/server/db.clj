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

(defn get-stand-unfiltered [id]
  (first
   (xt/q @node
         ['(fn [id-param]
             (-> (from :stands [xt/id *])
                 (where (= xt/id id-param))))
          id])))

(defn get-stand [id user-id]
  (first
   (xt/q @node
         ['(fn [id-param u]
             (-> (from :stands [xt/id *])
                 (where (and (= xt/id id-param)
                             (or (= creator u)
                                 (= shared? true))))))
          id user-id])))

(defn list-stands [user-id]
  (vec (xt/q @node
             ['(fn [u]
                 (-> (from :stands [*])
                     (where (or (= creator u)
                                (= shared? true)))))
              user-id])))

(defn save-user [user]
  (xt/submit-tx @node [[:put-docs :users (assoc user :updated (str (t/now)))]]))

(defn save-stand [stand]
  (xt/submit-tx @node [[:put-docs :stands (assoc stand :updated (str (t/now)))]]))

(defn delete-stand [id]
  (xt/submit-tx @node [[:delete-docs :stands id]]))
