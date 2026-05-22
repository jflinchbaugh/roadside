(ns com.hjsoft.mapmarks.website.state-test
  (:require
   [cljs.test :refer [deftest testing is]]
   [com.hjsoft.mapmarks.website.state :as sut]
   [com.hjsoft.mapmarks.website.storage :as storage]
   [com.hjsoft.mapmarks.website.config :as config]
   [com.hjsoft.mapmarks.website.utils :as utils]))

(def ^:const one-day (* 24 60 60 1000))

(deftest migrate-marks-test
  (testing "migrates tags to lowercase"
    (let [input [{:id "1" :tags ["Apples" "CORN"]}
                 {:id "2" :tags ["peaches"]}]
          expected [{:id "1" :tags ["apples" "corn"]}
                    {:id "2" :tags ["peaches"]}]
          result (sut/migrate-marks input)]
      (is (= expected result))))
  (testing "assigns IDs to marks without them"
    (let [input [{:tags ["apples"]}]
          result (sut/migrate-marks input)]
      (is (string? (:id (first result))))
      (is (= ["apples"] (:tags (first result)))))))

(deftest app-reducer-test
  (testing "set-marks"
    (testing "initial set"
      (is (= {:marks [{:id 1}]}
               (sut/app-reducer {} [:set-marks [{:id 1}]]))))
    (testing "merge new"
      (let [initial-state {:marks [{:id 1}]}
            result (sut/app-reducer initial-state [:set-marks [{:id 2}]])]
        (is (= #{{:id 1} {:id 2}} (set (:marks result))))))
    (testing "update existing"
      (let [initial-state {:marks [{:id 1 :v 1}]}
            result (sut/app-reducer initial-state [:set-marks [{:id 1 :v 2}]])]
        (is (= #{{:id 1 :v 2}} (set (:marks result)))))))

  (testing "set-tag-filter"
    (is (= {:tag-filter "Apples"}
             (sut/app-reducer {} [:set-tag-filter "Apples"]))))

  (testing "set-selected-mark"
    (is (= {:selected-mark {:name "My Mark"}}
             (sut/app-reducer {} [:set-selected-mark {:name "My Mark"}]))))

  (testing "set-map-center"
    (is (= {:map-center [1.0 2.0]}
             (sut/app-reducer {} [:set-map-center [1.0 2.0]]))))

  (testing "set-settings"
    (is (= {:settings {:user "test"}}
             (sut/app-reducer {} [:set-settings {:user "test"}]))))

  (testing "set-is-synced"
    (is (= {:is-synced true}
             (sut/app-reducer {} [:set-is-synced true]))))

  (testing "set-notification"
    (is (= {:notification {:type :success :message "hi"}}
             (sut/app-reducer {} [:set-notification {:type :success :message "hi"}])))
    (is (= {:notification {:type :updated}}
             (sut/app-reducer {:notification {:type :original}}
                              [:set-notification (fn [_] {:type :updated})]))))

  (testing "remove-mark"
    (is (= {:marks []}
             (sut/app-reducer {:marks [{:id "1" :name "A"}]}
                              [:remove-mark {:id "1" :name "A"}]))))

  (testing "update-mark"
    (let [initial-state {:marks [{:id "1" :name "Old Name"}]}
          result (sut/app-reducer initial-state [:update-mark {:id "1" :name "New Name"}])]
      (is (= [{:id "1" :name "New Name"}] (:marks result)))))

  (testing "vote preservation"
    (testing "merge preserves local score and user-vote"
      (let [initial-state {:marks [{:id "1" :name "Old Name" :score 5 :user-vote 1}]}
            result (sut/app-reducer initial-state [:set-marks [{:id "1" :name "New Name"}]])]
        (is (= [{:id "1" :name "New Name" :score 5 :user-vote 1}] (:marks result)))))
    (testing "sync preserves local score and user-vote"
      (let [initial-state {:marks [{:id "1" :name "Old Name" :score 5 :user-vote 1}]}
            result (sut/app-reducer initial-state [:sync-marks {:marks [{:id "1" :name "New Name"}]}])]
        (is (= [{:id "1" :name "New Name" :score 5 :user-vote 1}] (:marks result)))))))

(deftest select-marks-by-expiry-test
  (let [active-mark {:name "Active" :expiration (utils/in-days 7)}
        expired-mark {:name "Expired" :expiration "2020-01-01"}
        marks [active-mark expired-mark]]
    (testing "hiding expired marks (default)"
      (let [result (sut/select-marks-by-expiry {:marks marks :show-expired? false})]
        (is (= 1 (count result)))
        (is (= "Active" (:name (first result))))))

    (testing "showing expired marks"
      (let [result (sut/select-marks-by-expiry {:marks marks :show-expired? true})]
        (is (= 2 (count result)))
        (is (= #{"Active" "Expired"} (set (map :name result))))))))

(deftest initial-app-state-fallback-test
  (testing (str "initial-app-state falls back to legacy "
                sut/legacy-prefix "-")
    (with-redefs [config/config (assoc config/config :site "potholes")
                  storage/get-item
                  (fn [k]
                    (cond
                      (= k "potholes-marks") nil
                      (= k (str sut/legacy-prefix "-marks"))
                      [{:id "1" :tags ["Apple"]}]
                      (= k (str sut/legacy-prefix "-map-center"))
                      [10.0 20.0]
                      :else nil))]
      (let [state (sut/initial-app-state)]
        (is (= [{:id "1" :tags ["apple"]}] (:marks state)))
        (is (= [10.0 20.0] (:map-center state)))))))

(deftest initial-app-state-config-effect-test
  (testing "initial-app-state uses different keys depending on config/site"
    (testing "when site is potholes, loads potholes keys"
      (with-redefs [config/config (assoc config/config :site "potholes")
                    storage/get-item (fn [k]
                                       (when (= k "potholes-marks")
                                         [{:id "p1" :tags ["pothole"]}]))]
        (let [state (sut/initial-app-state)]
          (is (= [{:id "p1" :tags ["pothole"]}] (:marks state))))))

    (testing "when site is coffee-marks, loads coffee-marks keys"
      (with-redefs [config/config (assoc config/config :site "coffee-marks")
                    storage/get-item (fn [k]
                                       (when (= k "coffee-marks-marks")
                                         [{:id "c1" :tags ["coffee"]}]))]
        (let [state (sut/initial-app-state)]
          (is (= [{:id "c1" :tags ["coffee"]}] (:marks state))))))))
