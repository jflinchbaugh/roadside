(ns com.hjsoft.roadside.website.state-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.utils :as utils]))

(deftest select-filtered-stands-test
  (let [today (.substring (.toISOString (js/Date.)) 0 10)
        yesterday (.substring (.toISOString (js/Date. (- (js/Date.) (* 24 60 60 1000)))) 0 10)
        tomorrow (.substring (.toISOString (js/Date. (+ (js/Date.) (* 24 60 60 1000)))) 0 10)
        stands [{:id "1" :name "Expired Stand" :expiration yesterday :updated "2026-03-01T00:00:00Z"}
                {:id "2" :name "Active Stand" :expiration tomorrow :updated "2026-03-02T00:00:00Z"}
                {:id "3" :name "No Expiration" :expiration "" :updated "2026-03-03T00:00:00Z"}]]

    (testing "Hiding expired stands (default)"
      (let [result (state/select-filtered-stands {:stands stands
                                                 :product-filter nil
                                                 :show-expired? false})]
        (is (= 2 (count result)))
        (is (not-any? #(= "Expired Stand" (:name %)) result))))

    (testing "Showing expired stands"
      (let [result (state/select-filtered-stands {:stands stands
                                                 :product-filter nil
                                                 :show-expired? true})]
        (is (= 3 (count result)))
        (is (some #(= "Expired Stand" (:name %)) result))))

    (testing "Filtering by product combined with expiry"
      (let [stands-with-products (conj stands {:id "4" 
                                               :name "Expired Apples" 
                                               :expiration yesterday 
                                               :products ["Apples"]
                                               :updated "2026-03-04T00:00:00Z"})
            result-hidden (state/select-filtered-stands {:stands stands-with-products
                                                         :product-filter "Apples"
                                                         :show-expired? false})
            result-shown (state/select-filtered-stands {:stands stands-with-products
                                                        :product-filter "Apples"
                                                        :show-expired? true})]
        (is (empty? result-hidden) "Should be empty because the only matching stand is expired")
        (is (= 1 (count result-shown)) "Should show the expired stand when show-expired? is true")))))
