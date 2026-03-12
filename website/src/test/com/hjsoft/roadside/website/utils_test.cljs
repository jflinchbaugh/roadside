(ns com.hjsoft.roadside.website.utils-test
  (:require [com.hjsoft.roadside.website.utils :as sut]
            [clojure.string :as str]
            [cljs.test :refer [are deftest is testing]]))

(deftest get-current-timestamp
  (is (js/Date. (sut/get-current-timestamp))
        "current timestamp string is well-formed as a date"))

(deftest in-a-week
  (is (re-matches #"\d{4}-\d{2}-\d{2}" (sut/in-a-week))
        "iso date format")
  (is (not= (js/Date.) (js/Date. (sut/in-a-week)))
        "it's not now")
  (is (< (js/Date.) (js/Date. (sut/in-a-week)))
        "it's in the future")
  (is (= 7
           (- (int (/ (.getTime (js/Date. (sut/in-a-week))) 1000 60 60 24))
              (int (/ (.getTime (js/Date.)) 1000 60 60 24))))
        "7 days into the future"))

(deftest random-uuid-str-test
  (is (string? (sut/random-uuid-str)))
  (is (re-matches #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    (sut/random-uuid-str)))
  (is (not= (sut/random-uuid-str) (sut/random-uuid-str))))

(deftest get-all-unique-products
  (testing "empty product list for most errors"
    (are [stands]
           (= [] (sut/get-all-unique-products stands))
      nil
      []
      [nil]
      [{:other "thing"}]
      [{:products nil}]))
  (is
   (= ["other" "thing"]
      (sut/get-all-unique-products
       [{:products [" thing " "thing"]}
        {:products ["thing" "other"]}]))
   "unique products trimmed and sorted"))

(deftest parse-coordinates
  (are
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

(deftest make-map-link
  (are [expected input]
      (= expected (sut/make-map-link input))
    nil nil
    nil ""
    nil ","
    "geo:1,2" " 1, 2 "
    ))

(deftest stand-popup-html
  (testing "empty stand"
    (is (= "(no details)" (sut/stand-popup-html nil)))
    (is (= "(no details)" (sut/stand-popup-html {}))))

  (testing "simple stand"
    (is (= "<b>My Stand</b><br>"
             (sut/stand-popup-html {:name "My Stand"})))
    (is (= "<b>My Stand</b><br>Apples, Oranges<br>"
             (sut/stand-popup-html
               {:name "My Stand" :products ["Apples" "Oranges"]}))))

  (testing "XSS sanitization"
    (is (not (str/includes? (sut/stand-popup-html
                              {:name "<script>alert(1)</script>"}) "<script>")))
    (is (str/includes? (sut/stand-popup-html
                         {:name "<script>alert(1)</script>"}) "&lt;script&gt;"))
    (is (str/includes? (sut/stand-popup-html
                         {:products ["<b>bold</b>"]}) "&lt;b&gt;bold&lt;/b&gt;"))))
