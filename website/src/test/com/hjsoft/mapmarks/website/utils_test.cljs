(ns com.hjsoft.mapmarks.website.utils-test
  (:require [com.hjsoft.mapmarks.website.utils :as sut]
            [clojure.string :as str]
            [tick.core :as t]
            ["@js-joda/timezone"]
            [cljs.test :refer [are deftest is testing]]))

(deftest get-current-timestamp
  (t/with-clock (t/clock (t/instant "2023-01-01T12:34:56Z"))
    (is (= "2023-01-01T12:34:56Z" (sut/get-current-timestamp))
        "returns the mock current timestamp")))

(deftest format-timestamp-test
  (testing "formats ISO strings to local date/time"
    (let [;; Create a date in the LOCAL timezone with seconds
          local-zdt (t/in (t/date-time "2023-01-01T12:34:56") (t/zone))
          ;; Convert it to UTC ISO string
          iso-utc (str (t/instant local-zdt))
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
  (t/with-clock (t/clock (t/instant "2023-01-01T12:34:56Z"))
    (is (= "2023-01-08" (sut/in-days 7))
        "7 days into the future")))

(deftest random-uuid-str-test
  (is (string? (sut/random-uuid-str)))
  (is (re-matches #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    (sut/random-uuid-str)))
  (is (not= (sut/random-uuid-str) (sut/random-uuid-str))))

(deftest get-all-unique-tags
  (testing "empty tag list for most errors"
    (are [marks]
           (= [] (sut/get-all-unique-tags marks))
      nil
      []
      [nil]
      [{:other "thing"}]
      [{:tags nil}]))
  (is
   (= ["other" "thing"]
      (sut/get-all-unique-tags
       [{:tags [" thing " "thing"]}
        {:tags ["thing" "other"]}]))
   "unique tags trimmed and sorted")
  (is
   (= ["apples" "corn"]
      (sut/get-all-unique-tags
       [{:tags ["Apples" "corn"]}
        {:tags ["apples" "Corn"]}]))
   "unique tags should be all lowercase"))

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

(deftest mark-popup-html
  (testing "empty mark"
    (is (= "(no details)" (sut/mark-popup-html nil)))
    (is (= "(no details)" (sut/mark-popup-html {}))))

  (testing "simple mark"
    (is (= "<b>My Mark</b><br>"
             (sut/mark-popup-html {:name "My Mark"})))
    (is (= "<b>My Mark</b><br>Apples, Oranges<br>"
             (sut/mark-popup-html
               {:name "My Mark" :tags ["Apples" "Oranges"]}))))

  (testing "XSS sanitization"
    (is (not (str/includes? (sut/mark-popup-html
                              {:name "<script>alert(1)</script>"}) "<script>")))
    (is (str/includes? (sut/mark-popup-html
                         {:name "<script>alert(1)</script>"}) "&lt;script&gt;"))
    (is (str/includes? (sut/mark-popup-html
                         {:tags ["<b>bold</b>"]}) "&lt;b&gt;bold&lt;/b&gt;")))

  (testing "disabling fields"
    (let [mark {:name "My Mark" :tags ["Tag1"]}]
      (is (not (str/includes? (sut/mark-popup-html mark {:disable-name? true}) "My Mark")))
      (is (str/includes? (sut/mark-popup-html mark {:disable-name? true}) "Tag1"))

      (is (str/includes? (sut/mark-popup-html mark {:disable-tags? true}) "My Mark"))
      (is (not (str/includes? (sut/mark-popup-html mark {:disable-tags? true}) "Tag1")))

      (is (= "(no details)" (sut/mark-popup-html mark {:disable-name? true :disable-tags? true}))))))
