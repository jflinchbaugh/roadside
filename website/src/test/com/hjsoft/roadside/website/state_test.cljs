(ns com.hjsoft.roadside.website.state-test
  (:require [com.hjsoft.roadside.website.state :as sut]
            [cljs.test :as t]))

(t/deftest app-reducer-test
  (t/testing "set-stands"
    (t/is (= {:stands [{:id 1}]}
             (sut/app-reducer {} [:set-stands [{:id 1}]]))))

  (t/testing "set-show-form"
    (t/is (= {:show-form true}
             (sut/app-reducer {} [:set-show-form true]))))

  (t/testing "set-stand-form-data with value"
    (t/is (= {:stand-form-data {:name "New"}}
             (sut/app-reducer {} [:set-stand-form-data {:name "New"}]))))

  (t/testing "set-stand-form-data with function"
    (t/is (= {:stand-form-data {:name "Old"}}
             (sut/app-reducer {:stand-form-data {:name "Initial"}}
                             [:set-stand-form-data (fn [_] {:name "Old"})]))))

  (t/testing "set-product-filter"
    (t/is (= {:product-filter "Apples"}
             (sut/app-reducer {} [:set-product-filter "Apples"]))))

  (t/testing "set-selected-stand"
    (t/is (= {:selected-stand {:name "My Stand"}}
             (sut/app-reducer {} [:set-selected-stand {:name "My Stand"}]))))

  (t/testing "set-map-center"
    (t/is (= {:map-center [1.0 2.0]}
             (sut/app-reducer {} [:set-map-center [1.0 2.0]]))))

  (t/testing "set-show-settings-dialog"
    (t/is (= {:show-settings-dialog true}
             (sut/app-reducer {} [:set-show-settings-dialog true]))))

  (t/testing "set-settings-form-data"
    (t/is (= {:settings-form-data {:user "test"}}
             (sut/app-reducer {} [:set-settings-form-data {:user "test"}]))))

  (t/testing "set-settings"
    (t/is (= {:settings {:user "test"}}
             (sut/app-reducer {} [:set-settings {:user "test"}]))))

  (t/testing "set-is-synced"
    (t/is (= {:is-synced true}
             (sut/app-reducer {} [:set-is-synced true]))))

  (t/testing "set-notification"
    (t/is (= {:notification {:type :success :message "hi"}}
             (sut/app-reducer {} [:set-notification {:type :success :message "hi"}])))))
