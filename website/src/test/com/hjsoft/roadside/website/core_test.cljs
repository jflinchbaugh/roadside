(ns com.hjsoft.roadside.website.core-test
  (:require [com.hjsoft.roadside.website.core :as sut]
            [cljs.test :as t]))

(t/testing "things"
  (t/deftest example-test
    (t/is true)))

(t/testing "in-a-week"
  (t/deftest in-a-week
    (t/is (re-matches #"\d{4}-\d{2}-\d{2}" (sut/in-a-week))
      "iso date format")
    (t/is (not= (js/Date.) (js/Date. (sut/in-a-week)))
      "it's not now")
    (t/is (< (js/Date.) (js/Date. (sut/in-a-week)))
      "it's in the future")
    (t/is (= 7
            (- (int (/ (.getTime (js/Date. (sut/in-a-week))) 1000 60 60 24))
               (int (/ (.getTime (js/Date.)) 1000 60 60 24))))
      "7 days into the future")))

(t/testing "stand-key"
  (t/deftest stand-key
    (t/is (= "-----" (sut/stand-key nil))
      "nil key")
    (t/is (= "-----" (sut/stand-key {}))
      "empty key")
    (t/is (= "name-1-2-address-town-state-prod-thing"
            (sut/stand-key {:name "name"
                            :coordinate [1.0 2.0]
                            :address "address"
                            :town "town"
                            :state "state"
                            :products ["prod" "thing"]}))
      "stand key built from fields")))
