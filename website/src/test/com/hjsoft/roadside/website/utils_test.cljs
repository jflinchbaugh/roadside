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

(t/deftest stand-key
  (t/is (= "|||||" (sut/stand-key nil))
        "nil key")
  (t/is (= "|||||" (sut/stand-key {}))
        "empty key")
  (t/is (= "my-uuid" (sut/stand-key {:id "my-uuid"}))
        "id-based key")
  (t/is (= "name|[1 2]|address|town|state|prod,thing"
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

(t/deftest make-map-link
  (t/are [expected input]
      (= expected (sut/make-map-link input))
    nil nil
    nil ""
    nil ","
    "geo:1,2" " 1, 2 "
    ))

(t/deftest stand-popup-html
  (t/testing "empty stand"
    (t/is (= "(no details)" (sut/stand-popup-html nil)))
    (t/is (= "(no details)" (sut/stand-popup-html {}))))

  (t/testing "simple stand"
    (t/is (= "<b>My Stand</b><br>"
             (sut/stand-popup-html {:name "My Stand"})))
    (t/is (= "<b>My Stand</b><br>Apples, Oranges<br>"
             (sut/stand-popup-html {:name "My Stand" :products ["Apples" "Oranges"]}))))

  (t/testing "XSS sanitization"
    (t/is (not (str/includes? (sut/stand-popup-html {:name "<script>alert(1)</script>"}) "<script>")))
    (t/is (str/includes? (sut/stand-popup-html {:name "<script>alert(1)</script>"}) "&lt;script&gt;"))
    (t/is (str/includes? (sut/stand-popup-html {:products ["<b>bold</b>"]}) "&lt;b&gt;bold&lt;/b&gt;"))))
