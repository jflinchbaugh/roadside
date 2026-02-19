(ns com.hjsoft.roadside.website.domain.stand-test
  (:require [com.hjsoft.roadside.website.domain.stand :as sut]
            [cljs.test :as t]))

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
