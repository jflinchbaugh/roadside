(ns com.hjsoft.roadside.website.state-test
  (:require [com.hjsoft.roadside.website.state :as sut]
            [cljs.test :as t]))

(t/deftest app-reducer-test
  (t/testing "set-stands"
    (t/testing "initial set"
      (t/is (= {:stands [{:id 1}]}
               (sut/app-reducer {} [:set-stands [{:id 1}]]))))
    (t/testing "merge new"
      (let [initial-state {:stands [{:id 1}]}
            result (sut/app-reducer initial-state [:set-stands [{:id 2}]])]
        (t/is (= #{{:id 1} {:id 2}} (set (:stands result))))))
    (t/testing "update existing"
      (let [initial-state {:stands [{:id 1 :v 1}]}
            result (sut/app-reducer initial-state [:set-stands [{:id 1 :v 2}]])]
        (t/is (= #{{:id 1 :v 2}} (set (:stands result)))))))

  (t/testing "set-product-filter"
    (t/is (= {:product-filter "Apples"}
             (sut/app-reducer {} [:set-product-filter "Apples"]))))

  (t/testing "set-selected-stand"
    (t/is (= {:selected-stand {:name "My Stand"}}
             (sut/app-reducer {} [:set-selected-stand {:name "My Stand"}]))))

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

  (t/testing "remove-stand"
    (t/is (= {:stands []}
             (sut/app-reducer {:stands [{:id "1" :name "A"}]}
                              [:remove-stand {:id "1" :name "A"}])))))

(t/deftest select-filtered-stands-test
  (let [stands [{:name "B" :updated "2023-01-01T12:00:00Z" :products ["Apples"]}
                {:name "A" :updated "2023-01-02T12:00:00Z" :products ["Corn"]}
                {:name "C" :updated "2023-01-01T10:00:00Z" :products ["Apples"]}]]
    (t/testing "sorting by updated date (descending)"
      (let [result (sut/select-filtered-stands {:stands stands})]
        (t/is (= ["A" "B" "C"] (map :name result)))))

    (t/testing "filtering by product"
      (let [result (sut/select-filtered-stands {:stands stands :product-filter "Apples"})]
        (t/is (= ["B" "C"] (map :name result)))))))

(t/deftest select-stands-by-expiry-test
  (let [active-stand {:name "Active" :expiration (com.hjsoft.roadside.website.utils/in-a-week)}
        expired-stand {:name "Expired" :expiration "2020-01-01"}
        stands [active-stand expired-stand]]
    (t/testing "hiding expired stands (default)"
      (let [result (sut/select-stands-by-expiry {:stands stands :show-expired? false})]
        (t/is (= 1 (count result)))
        (t/is (= "Active" (:name (first result))))))

    (t/testing "showing expired stands"
      (let [result (sut/select-stands-by-expiry {:stands stands :show-expired? true})]
        (t/is (= 2 (count result)))
        (t/is (= #{"Active" "Expired"} (set (map :name result))))))))
