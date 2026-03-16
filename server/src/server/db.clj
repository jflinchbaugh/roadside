(ns server.db
  (:require [xtdb.api :as xt]
            [tick.core :as t]))

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

(defn cleanup-stands!
  "Remove transient fields from all stands in the database."
  [node transient-fields]
  (let [stands (xt/q node '(from :stands [*]))
        to-update (keep (fn [stand]
                          (let [clean-stand (apply dissoc stand transient-fields)]
                            (when (not= stand clean-stand)
                              clean-stand)))
                        stands)]
    (when (seq to-update)
      (xt/submit-tx node (mapv (fn [s] [:put-docs :stands s]) to-update)))))
