(ns com.hjsoft.roadside.website.domain.stand-test
  (:require [com.hjsoft.roadside.website.domain.stand :as sut]
            [cljs.test :refer [deftest is testing]]))

(deftest stand-key-test
  (testing "nil or empty stand"
    (is (nil? (sut/stand-key nil)))
    (is (= "|||||" (sut/stand-key {}))))

  (testing "id-based key"
    (is (= "my-uuid" (sut/stand-key {:id "my-uuid"}))))

  (testing "content-based key"
    (is (= "name|[1 2]|address|town|state|prod,thing"
           (sut/stand-key {:name "name"
                           :coordinate [1.0 2.0]
                           :address "address"
                           :town "town"
                           :state "state"
                           :products ["prod" "thing"]})))))

(deftest infer-products-test
  (let [all-products ["Apples" "Corn" "Peaches"]]
    (testing "detects products from name"
      (is (= ["Apples"] (sut/infer-products "Fresh Apples" [] all-products)))
      (is (= ["Corn" "Peaches"] (sut/infer-products "Corn and Peaches" [] all-products))))

    (testing "doesn't duplicate existing products"
      (is (= ["Apples"] (sut/infer-products "Fresh Apples" ["Apples"] all-products))))

    (testing "handles nil or empty names"
      (is (= [] (sut/infer-products nil [] all-products)))
      (is (= [] (sut/infer-products "" [] all-products))))))

(deftest init-form-state-test
  (testing "initializes with defaults when no editing-stand"
    (let [map-center [40.5 -76.5]
          state (sut/init-form-state {:map-center map-center})]
      (is (= "40.5, -76.5" (:coordinate state)))
      (is (= "" (:name state)))
      (is (false? (:show-address? state)))
      (is (= "" (:current-product state)))))

  (testing "initializes from editing-stand and detects show-address?"
    (let [editing {:name "Existing" :address "123 Main St"}
          state (sut/init-form-state {:editing-stand editing})]
      (is (= "Existing" (:name state)))
      (is (true? (:show-address? state)))
      (is (= "" (:current-product state))))))

(deftest stand-form-reducer-test
  (testing "update-field"
    (let [state {:name ""}
          next-state (sut/stand-form-reducer
                       state
                       [:update-field [:name "New Name"]])]
      (is (= "New Name" (:name next-state)))))

  (testing "update-current-product"
    (let [state {:current-product ""}
          next-state (sut/stand-form-reducer
                       state
                       [:update-current-product "new product"])]
      (is (= "new product" (:current-product next-state)))))

  (testing "add-product"
    (let [state {:products [] :current-product "  Apples  "}
          state1 (sut/stand-form-reducer state [:add-product])]
      (is (= ["Apples"] (:products state1)))
      (is (= "" (:current-product state1)))))

  (testing "prevent duplicate products"
    (let [state {:products ["Apples"] :current-product "Apples"}
          next-state (sut/stand-form-reducer state [:add-product])]
      (is (= ["Apples"] (:products next-state)))
      (is (= "" (:current-product next-state)))))

  (testing "toggle-address"
    (let [state {:show-address? false}
          state1 (sut/stand-form-reducer state [:toggle-address])
          state2 (sut/stand-form-reducer state1 [:toggle-address])]
      (is (true? (:show-address? state1)))
      (is (false? (:show-address? state2))))))

(deftest prepare-submit-data-test
  (testing "adds pending current product"
    (let [state {:products ["Corn"]
                 :current-product "Apples"}
          final (sut/prepare-submit-data state)]
      (is (= ["Corn" "Apples"] (:products final)))
      (is (nil? (:current-product final)))))

  (testing "empty current-product adds nothing to products"
    (let [state {:products ["Corn"]
                 :current-product ""}
          final (sut/prepare-submit-data state)]
      (is (= ["Corn"] (:products final)))
      (is (nil? (:current-product final))))))

(deftest add-and-edit-stand-test
  (let [stands [{:id "1"
                 :name "Apple Farm"
                 :products ["apples"]
                 :coordinate "1,2"}]]
    (testing "adding a new stand with auto-product detection"
      (let [result (sut/add-stand
                    {:name "Better Apples"
                     :coordinate "3,4"
                     :products []}
                    stands
                    "test-user")]
        (is (:success result))
        (is (some #(= "apples" %) (:products (:processed-data result)))
            "Automatically added Apples
               because it was in the name and exists
               in other stands")
        (is (= "test-user" (:creator (:processed-data result))))))

    (testing "adding a stand with empty name"
      (let [result (sut/add-stand
                    {:name ""
                     :coordinate "3,4"
                     :products ["Apples"]}
                    stands
                    "test-user")]
        (is (:success result))
        (is (= "" (:name (:processed-data result))))))

    (testing "preventing duplicates in add-stand"
      (let [result (sut/add-stand
                    {:id "1" :name "Apple Farm" :coordinate "1,2" :products ["apples"]}
                    stands
                    "test-user")]
        (is (not (:success result)))
        (is (= "This stand already exists!" (:error result)))))

    (testing "editing stand replaces the old one and DOES NOT auto-detect products"
      (let [stands [{:id "1" :name "Original" :products ["apples"] :coordinate "1,2"}
                    {:id "2" :name "Corn Stand" :products ["corn"] :coordinate "3,4"}]
            result (sut/edit-stand
                    {:id "1"
                     :name "Original and corn"
                     :products ["apples"]
                     :coordinate "1,2"}
                    stands
                    (first stands)
                    "test-user")
            {:keys [success processed-data stands]} result]
        (is success)
        (is (:updated processed-data))
        (is (= {:id "1"
                :name "Original and corn"
                :coordinate "1,2"
                :products ["apples"]}
               (dissoc processed-data :updated :creator)))
        (is (not (some #(= "corn" %) (:products processed-data)))
            "Should NOT have added corn even though it is in the name and exists elsewhere")
        (is (= [{:id "1"
                 :name "Original and corn"
                 :coordinate "1,2"
                 :products ["apples"]}
                {:id "2"
                 :name "Corn Stand"
                 :products ["corn"]
                 :coordinate "3,4"}]
               (map (fn [s] (dissoc s :updated :creator)) stands)))))))
