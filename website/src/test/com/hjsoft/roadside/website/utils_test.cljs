(ns com.hjsoft.roadside.website.utils-test
  (:require [com.hjsoft.roadside.website.utils :as sut]
            [clojure.string :as str]
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

(t/deftest random-uuid-str-test
  (t/is (string? (sut/random-uuid-str)))
  (t/is (re-matches #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    (sut/random-uuid-str)))
  (t/is (not= (sut/random-uuid-str) (sut/random-uuid-str))))

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

(t/deftest make-map-link
  (t/are [expected input]
      (= expected (sut/make-map-link input))
    nil nil
    nil ""
    nil ","
    "geo:1,2" " 1, 2 "
    ))

(t/deftest show-system-notification-test
  (reset! init/notification-calls [])
  (sut/show-system-notification "Test Notification" {:body "This is a test"})
  (let [calls @init/notification-calls]
    (t/is (= 1 (count calls)) "One notification should be called")
    (t/is (= "Test Notification" (:title (first calls))) "Title matches")
    (t/is (= "This is a test" (get-in (first calls) [:options :body])) "Body matches")))

