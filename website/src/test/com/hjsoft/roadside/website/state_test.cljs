(ns com.hjsoft.roadside.website.state-test
  (:require
   [cljs.test :refer [deftest testing is]]
   [com.hjsoft.roadside.website.state :as sut]
   [com.hjsoft.roadside.website.utils :as utils]))

(def ^:const one-day (* 24 60 60 1000))

(deftest app-reducer-test
  (testing "set-stands"
    (testing "initial set"
      (is (= {:stands [{:id 1}]}
               (sut/app-reducer {} [:set-stands [{:id 1}]]))))
    (testing "merge new"
      (let [initial-state {:stands [{:id 1}]}
            result (sut/app-reducer initial-state [:set-stands [{:id 2}]])]
        (is (= #{{:id 1} {:id 2}} (set (:stands result))))))
    (testing "update existing"
      (let [initial-state {:stands [{:id 1 :v 1}]}
            result (sut/app-reducer initial-state [:set-stands [{:id 1 :v 2}]])]
        (is (= #{{:id 1 :v 2}} (set (:stands result)))))))

  (testing "set-product-filter"
    (is (= {:product-filter "Apples"}
             (sut/app-reducer {} [:set-product-filter "Apples"]))))

  (testing "set-selected-stand"
    (is (= {:selected-stand {:name "My Stand"}}
             (sut/app-reducer {} [:set-selected-stand {:name "My Stand"}]))))

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

  (testing "remove-stand"
    (is (= {:stands []}
             (sut/app-reducer {:stands [{:id "1" :name "A"}]}
                              [:remove-stand {:id "1" :name "A"}])))))

(deftest select-stands-by-expiry-test
  (let [active-stand {:name "Active" :expiration (utils/in-a-week)}
        expired-stand {:name "Expired" :expiration "2020-01-01"}
        stands [active-stand expired-stand]]
    (testing "hiding expired stands (default)"
      (let [result (sut/select-stands-by-expiry {:stands stands :show-expired? false})]
        (is (= 1 (count result)))
        (is (= "Active" (:name (first result))))))

    (testing "showing expired stands"
      (let [result (sut/select-stands-by-expiry {:stands stands :show-expired? true})]
        (is (= 2 (count result)))
        (is (= #{"Active" "Expired"} (set (map :name result))))))))
