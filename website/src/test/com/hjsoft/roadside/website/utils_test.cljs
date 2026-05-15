(ns com.hjsoft.roadside.website.utils-test
  (:require [com.hjsoft.roadside.website.utils :as sut]
            [clojure.string :as str]
            [cljs.test :refer [are deftest is testing]]))

(deftest get-current-timestamp
  (is (js/Date. (sut/get-current-timestamp))
        "current timestamp string is well-formed as a date"))

(deftest format-timestamp-test
  (testing "formats ISO strings to local date/time"
    (let [;; Create a date in the LOCAL timezone with seconds
          local-date (js/Date. 2023 0 1 12 34 56)
          ;; Convert it to UTC ISO string
          iso-utc (.toISOString local-date)
          ;; The formatter should convert it back to local time and truncate seconds
          expected "2023-01-01 12:34"]
      (is (= expected (sut/format-timestamp iso-utc))
          "Converts UTC ISO string back to local time and truncates seconds")))
  (testing "handles nil or empty string"
    (is (nil? (sut/format-timestamp nil)))
    (is (nil? (sut/format-timestamp ""))))
  (testing "handles invalid date strings by returning them"
    (is (= "not-a-date" (sut/format-timestamp "not-a-date")))))

(deftest in-days
  (is (re-matches #"\d{4}-\d{2}-\d{2}" (sut/in-days 7))
        "iso date format")
  (is (not= (js/Date.) (js/Date. (sut/in-days 7)))
        "it's not now")
  (is (< (js/Date.) (js/Date. (sut/in-days 7)))
        "it's in the future")
  (is (= 7
           (- (int (/ (.getTime (js/Date. (sut/in-days 7))) 1000 60 60 24))
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
   "unique products trimmed and sorted")
  (is
   (= ["apples" "corn"]
      (sut/get-all-unique-products
       [{:products ["Apples" "corn"]}
        {:products ["apples" "Corn"]}]))
   "unique products should be all lowercase"))

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

(deftest mobile?-test
  (testing "mobile detection"
    (is (sut/mobile? #js {:userAgent "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X)"}))
    (is (sut/mobile? #js {:userAgent "Mozilla/5.0 (Linux; Android 10; SM-G981B)"}))
    (is (not (sut/mobile? #js {:userAgent "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})))
    (is (not (sut/mobile? nil)))))

(deftest make-map-link
  (testing "nil/empty cases"
    (is (nil? (sut/make-map-link nil nil)))
    (is (nil? (sut/make-map-link "" ""))))

  (testing "mobile (geo:)"
    (are [expected lat lon]
         (= expected (sut/make-map-link lat lon true))
      "geo:1,2" 1 2
      "geo:1,2" "1" "2"))

  (testing "desktop (google maps)"
    (are [expected lat lon]
         (= expected (sut/make-map-link lat lon false))
      "https://www.google.com/maps/search/?api=1&query=1,2" 1 2
      "https://www.google.com/maps/search/?api=1&query=1,2" "1" "2")))

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
