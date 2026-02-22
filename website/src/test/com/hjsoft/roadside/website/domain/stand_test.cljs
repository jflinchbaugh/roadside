(ns com.hjsoft.roadside.website.domain.stand-test
  (:require [com.hjsoft.roadside.website.domain.stand :as sut]
            [cljs.test :as t]))

(t/deftest init-form-state-test
  (t/testing "initializes with defaults when no editing-stand"
    (let [map-center [40.5 -76.5]
          state (sut/init-form-state {:map-center map-center})]
      (t/is (= "40.5, -76.5" (:coordinate state)))
      (t/is (= "" (:name state)))
      (t/is (false? (:show-address? state)))
      (t/is (= "" (:current-product state)))))

  (t/testing "initializes from editing-stand and detects show-address?"
    (let [editing {:name "Existing" :address "123 Main St"}
          state (sut/init-form-state {:editing-stand editing})]
      (t/is (= "Existing" (:name state)))
      (t/is (true? (:show-address? state)))
      (t/is (= "" (:current-product state))))))

(t/deftest stand-form-reducer-test
  (t/testing "update-field"
    (let [state {:name ""}
          next-state (sut/stand-form-reducer state [:update-field [:name "New Name"]])]
      (t/is (= "New Name" (:name next-state)))))

  (t/testing "add-product"
    (let [state {:products [] :current-product "  Apples  "}
          ;; Test adding from current-product
          state1 (sut/stand-form-reducer state [:add-product])
          ;; Test adding explicitly
          state2 (sut/stand-form-reducer state1 [:add-product "Corn"])]
      (t/is (= ["Apples"] (:products state1)))
      (t/is (= "" (:current-product state1)))
      (t/is (= ["Apples" "Corn"] (:products state2)))))

  (t/testing "prevent duplicate products"
    (let [state {:products ["Apples"] :current-product "Apples"}
          next-state (sut/stand-form-reducer state [:add-product])]
      (t/is (= ["Apples"] (:products next-state)))
      (t/is (= "" (:current-product next-state)))))

  (t/testing "toggle-address"
    (let [state {:show-address? false}
          state1 (sut/stand-form-reducer state [:toggle-address])
          state2 (sut/stand-form-reducer state1 [:toggle-address])]
      (t/is (true? (:show-address? state1)))
      (t/is (false? (:show-address? state2))))))

(t/deftest prepare-submit-data-test
  (t/testing "cleans up transient UI state and adds pending product"
    (let [state {:name "My Stand"
                 :products ["Corn"]
                 :current-product "Apples"
                 :show-address? true}
          final (sut/prepare-submit-data state)]
      (t/is (= ["Corn" "Apples"] (:products final)))
      (t/is (not (contains? final :current-product)))
      (t/is (not (contains? final :show-address?)))
      (t/is (= "My Stand" (:name final)))))

  (t/testing "handles empty current-product"
    (let [state {:name "My Stand"
                 :products ["Corn"]
                 :current-product ""
                 :show-address? false}
          final (sut/prepare-submit-data state)]
      (t/is (= ["Corn"] (:products final)))
      (t/is (not (contains? final :current-product))))))

(t/deftest process-stand-form-test
  (let [stands [{:id "1" :name "Apple Farm" :products ["apples"] :coordinate "1,2"}]]
    (t/testing "adding a new stand with auto-product detection"
      (let [result (sut/process-stand-form
                    {:name "Better Apples" :coordinate "3,4" :products []}
                    stands
                    nil
                    "test-user")]
        (t/is (:success result))
        (t/is (some #(= "apples" %) (:products (:processed-data result)))
            "Automatically added Apples
               because it was in the name and exists
               in other stands")
        (t/is (= "test-user" (:creator (:processed-data result))))))

    (t/testing "preventing duplicates"
      (let [result (sut/process-stand-form
                    {:id "1" :name "Apple Farm" :coordinate "1,2" :products ["apples"]}
                    stands
                    nil
                    "test-user")]
        (t/is (not (:success result)))
        (t/is (= "This stand already exists!" (:error result)))))
    (t/testing "editing stand replaces the old one"
      (let [result (sut/process-stand-form
                    {:id "1"
                     :name "New Apple Farm"
                     :coordinate "3,4"
                     :products ["apples" "oranges"]}
                    stands
                    (first stands)
                    "test-user")
            {:keys [success processed-data stands]} result]
        (t/is success)

        (t/is (:updated processed-data))
        (t/is (= {:id "1"
                :name "New Apple Farm"
                :coordinate "3,4"
                :products ["apples" "oranges"]}
               (dissoc processed-data :updated :creator)))
        (t/is (= [{:id "1"
                   :name "New Apple Farm"
                   :coordinate "3,4"
                   :products ["apples" "oranges"]}]
                 (map (fn [s] (dissoc s :updated :creator)) stands)))))))
