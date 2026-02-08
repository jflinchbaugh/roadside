(ns com.hjsoft.roadside.website.domain.stand-test
  (:require [com.hjsoft.roadside.website.domain.stand :as sut]
            [cljs.test :as t]))

(t/deftest process-stand-form-test
  (let [stands [{:name "Apple Farm" :products ["Apples"] :coordinate "1,2"}]]
    (t/testing "adding a new stand with auto-product detection"
      (let [result (sut/process-stand-form
                    {:name "Better Apples" :coordinate "3,4" :products []}
                    stands
                    nil)]
        (t/is (:success result))
        (t/is (some #(= "Apples" %) (:products (first (:stands result))))
              "Automatically added Apples because it was in the name and exists in other stands")))

    (t/testing "preventing duplicates"
      (let [result (sut/process-stand-form
                    {:name "Apple Farm" :coordinate "1,2" :products ["Apples"]}
                    stands
                    nil)]
        (t/is (not (:success result)))
        (t/is (= "This stand already exists!" (:error result)))))))
