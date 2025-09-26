(ns com.hjsoft.roadside.website.core-test
  (:require [com.hjsoft.roadside.website.core :as sut]
            [cljs.test :as t]))

(t/deftest get-current-timestamp
  (t/is (js/Date. (sut/get-current-timestamp))
        "current timestamp string is well-formed as a date"))

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
        "7 days into the future"))

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
        "stand key built from fields"))

(t/deftest get-all-unique-products
  (t/testing "empty product list for most errors"
    (t/are [stands]
           (= [] (sut/get-all-unique-products stands))
      nil
      []
      [nil]
      [{:other "thing"}]
      [{:products nil}]))
  (t/is
   (= ["other" "thing"]
      (sut/get-all-unique-products
       [{:products [" thing " "thing"]}
        {:products ["thing" "other"]}]))
   "unique products trimmed and sorted"))

(t/deftest parse-coordinates
  (t/are
   [expected provided]
   (= expected (sut/parse-coordinates provided))
    nil nil
    nil ""
    nil "x"
    nil "10"
    nil "10.0,x"
    nil "x,10.0"
    nil "10.0,12.0,13.0"
    [10.0 12.0] "10.0,12.0"
    [10.0 12.0] " 10.0, 12.0 "
    [-10.0 -12.0] "-10.0, -12.0"))
