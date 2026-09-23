(ns com.hjsoft.mapmarks.website.state-test
  (:require
   [cljs.test :as t]
   [com.hjsoft.mapmarks.website.state :as sut]
   [com.hjsoft.mapmarks.website.storage :as storage]
   [com.hjsoft.mapmarks.website.config :as config]
   [com.hjsoft.mapmarks.website.utils :as utils]))

(def ^:const one-day (* 24 60 60 1000))

(t/deftest migrate-marks-test
  (t/testing "migrates tags to lowercase"
    (let [input [{:id "1" :tags ["Apples" "CORN"]}
                 {:id "2" :tags ["peaches"]}]
          expected [{:id "1" :tags ["apples" "corn"]}
                    {:id "2" :tags ["peaches"]}]
          result (sut/migrate-marks input)]
      (t/is (= expected result))))
  (t/testing "assigns IDs to marks without them"
    (let [input [{:tags ["apples"]}]
          result (sut/migrate-marks input)]
      (t/is (string? (:id (first result))))
      (t/is (= ["apples"] (:tags (first result)))))))

(t/deftest app-reducer-test
  (t/testing "set-marks"
    (t/testing "initial set"
      (t/is (= {:marks [{:id 1}]}
               (sut/app-reducer {} [:set-marks [{:id 1}]]))))
    (t/testing "merge new"
      (let [initial-state {:marks [{:id 1}]}
            result (sut/app-reducer initial-state [:set-marks [{:id 2}]])]
        (t/is (= #{{:id 1} {:id 2}} (set (:marks result))))))
    (t/testing "update existing"
      (let [initial-state {:marks [{:id 1 :v 1}]}
            result (sut/app-reducer initial-state [:set-marks [{:id 1 :v 2}]])]
        (t/is (= #{{:id 1 :v 2}} (set (:marks result)))))))

  (t/testing "set-tag-filter"
    (t/is (= {:tag-filter "Apples"}
             (sut/app-reducer {} [:set-tag-filter "Apples"]))))

  (t/testing "set-selected-mark"
    (t/is (= {:selected-mark {:name "My Mark"}}
             (sut/app-reducer {} [:set-selected-mark {:name "My Mark"}]))))

  (t/testing "set-map-center"
    (t/is (= {:map-center [1.0 2.0]}
             (sut/app-reducer {} [:set-map-center [1.0 2.0]]))))

  (t/testing "set-settings"
    (t/is (= {:settings {:user "test"}}
             (sut/app-reducer {} [:set-settings {:user "test"}]))))

  (t/testing "set-is-synced"
    (t/is (= {:is-synced true}
             (sut/app-reducer {} [:set-is-synced true]))))

  (t/testing "set-notification"
    (t/is (= {:notification {:type :success :message "hi"}}
             (sut/app-reducer {} [:set-notification {:type :success :message "hi"}])))
    (t/is (= {:notification {:type :updated}}
             (sut/app-reducer {:notification {:type :original}}
                              [:set-notification (fn [_] {:type :updated})]))))

  (t/testing "remove-mark"
    (t/is (= {:marks []}
             (sut/app-reducer {:marks [{:id "1" :name "A"}]}
                              [:remove-mark {:id "1" :name "A"}]))))

  (t/testing "update-mark"
    (let [initial-state {:marks [{:id "1" :name "Old Name"}]}
          result (sut/app-reducer initial-state [:update-mark {:id "1" :name "New Name"}])]
      (t/is (= [{:id "1" :name "New Name"}] (:marks result)))))

  (t/testing "vote preservation"
    (t/testing "merge preserves local score and user-vote"
      (let [initial-state {:marks [{:id "1" :name "Old Name" :score 5 :user-vote 1}]}
            result (sut/app-reducer initial-state [:set-marks [{:id "1" :name "New Name"}]])]
        (t/is (= [{:id "1" :name "New Name" :score 5 :user-vote 1}] (:marks result)))))
    (t/testing "sync preserves local score and user-vote"
      (let [initial-state {:marks [{:id "1" :name "Old Name" :score 5 :user-vote 1}]}
            result (sut/app-reducer initial-state [:sync-marks {:marks [{:id "1" :name "New Name"}]}])]
        (t/is (= [{:id "1" :name "New Name" :score 5 :user-vote 1}] (:marks result)))))))

(t/deftest select-marks-by-expiry-test
  (let [active-mark {:name "Active" :expiration (utils/in-days 7)}
        expired-mark {:name "Expired" :expiration "2020-01-01"}
        marks [active-mark expired-mark]]
    (t/testing "hiding expired marks (default)"
      (let [result (sut/select-marks-by-expiry {:marks marks :show-expired? false})]
        (t/is (= 1 (count result)))
        (t/is (= "Active" (:name (first result))))))

    (t/testing "showing expired marks"
      (let [result (sut/select-marks-by-expiry {:marks marks :show-expired? true})]
        (t/is (= 2 (count result)))
        (t/is (= #{"Active" "Expired"} (set (map :name result))))))))

(t/deftest initial-app-state-fallback-test
  (t/testing (str "initial-app-state falls back to legacy "
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
        (t/is (= [{:id "1" :tags ["apple"]}] (:marks state)))
        (t/is (= [10.0 20.0] (:map-center state)))))))

(t/deftest initial-app-state-config-effect-test
  (t/testing "initial-app-state uses different keys depending on config/site"
    (t/testing "when site is potholes, loads potholes keys"
      (with-redefs [config/config (assoc config/config :site "potholes")
                    storage/get-item (fn [k]
                                       (when (= k "potholes-marks")
                                         [{:id "p1" :tags ["pothole"]}]))]
        (let [state (sut/initial-app-state)]
          (t/is (= [{:id "p1" :tags ["pothole"]}] (:marks state))))))

    (t/testing "when site is coffee-marks, loads coffee-marks keys"
      (with-redefs [config/config (assoc config/config :site "coffee-marks")
                    storage/get-item (fn [k]
                                       (when (= k "coffee-marks-marks")
                                         [{:id "c1" :tags ["coffee"]}]))]
        (let [state (sut/initial-app-state)]
          (t/is (= [{:id "c1" :tags ["coffee"]}] (:marks state))))))))

(t/deftest follow-user-state-test
  (t/testing "set-follow-user updates follow-user? flag"
    (let [s1 (sut/app-reducer {} [:set-follow-user true])
          s2 (sut/app-reducer s1 [:set-follow-user false])]
      (t/is (= true (:follow-user? s1)))
      (t/is (= false (:follow-user? s2)))))

  (t/testing "selecting a mark sets follow-user? to false"
    (let [s (sut/app-reducer {:follow-user? true}
                             [:set-selected-mark {:id "123"}])]
      (t/is (= false (:follow-user? s)))))

  (t/testing (str "initial-app-state defaults follow-user? to true "
                "when no mark selected")
    (with-redefs [storage/get-item (constantly nil)]
      (let [state (sut/initial-app-state)]
        (t/is (= true (:follow-user? state)))))))

(t/deftest review-state-test
  (t/testing "set-review-mode reducer"
    (let [s (sut/app-reducer {} [:set-review-mode true])]
      (t/is (= true (:review-mode? s)))))

  (t/testing "set-last-reviewed reducer"
    (let [s (sut/app-reducer {} [:set-last-reviewed "2026-09-22"])]
      (t/is (= "2026-09-22" (:last-reviewed s))))))

(t/deftest select-review-marks-test
  (let [alice-expired {:id "1" :creator "alice" :expiration "2026-08-01"}
        alice-active  {:id "2" :creator "alice" :expiration "2026-09-30"}
        bob-expired   {:id "3" :creator "bob"   :expiration "2026-07-01"}
        local-mark    {:id "4" :creator nil     :expiration "2026-08-15"}
        marks [alice-expired alice-active bob-expired local-mark]]
    (t/testing "filters only owned marks and sorts chronologically"
      (let [result (sut/select-review-marks
                    {:marks marks
                     :settings {:user "alice"}})]
        (t/is (= ["1" "4" "2"] (mapv :id result)))))
    (t/testing "local user owns only creator-less marks"
      (let [result (sut/select-review-marks
                    {:marks marks
                     :settings {:user nil}})]
        (t/is (= ["4"] (mapv :id result)))))))
