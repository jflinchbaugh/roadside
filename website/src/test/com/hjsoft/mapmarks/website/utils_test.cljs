(ns com.hjsoft.mapmarks.website.utils-test
  (:require [com.hjsoft.mapmarks.website.utils :as sut]
            [clojure.string :as str]
            [tick.core :as tick]
            ["@js-joda/timezone"]
            [cljs.test :as t]))

(t/deftest get-current-timestamp
  (tick/with-clock (tick/clock (tick/instant "2023-01-01T12:34:56Z"))
    (t/is (= "2023-01-01T12:34:56Z" (sut/get-current-timestamp))
        "returns the mock current timestamp")))

(t/deftest format-timestamp-test
  (t/testing "formats ISO strings to local date/time"
    (let [;; Create a date in the LOCAL timezone with seconds
          local-zdt (tick/in (tick/date-time "2023-01-01T12:34:56") (tick/zone))
          ;; Convert it to UTC ISO string
          iso-utc (str (tick/instant local-zdt))
          ;; The formatter should convert it back to local time and truncate seconds
          expected "2023-01-01 12:34"]
      (t/is (= expected (sut/format-timestamp iso-utc))
          "Converts UTC ISO string back to local time and truncates seconds")))
  (t/testing "handles nil or empty string"
    (t/is (nil? (sut/format-timestamp nil)))
    (t/is (nil? (sut/format-timestamp ""))))
  (t/testing "handles invalid date strings by returning them"
    (t/is (= "not-a-date" (sut/format-timestamp "not-a-date")))))

(t/deftest in-days
  (tick/with-clock (tick/clock (tick/instant "2023-01-01T12:34:56Z"))
    (t/is (= "2023-01-08" (sut/in-days 7))
        "7 days into the future")))

(t/deftest random-uuid-str-test
  (t/is (string? (sut/random-uuid-str)))
  (t/is (re-matches #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    (sut/random-uuid-str)))
  (t/is (not= (sut/random-uuid-str) (sut/random-uuid-str))))

(t/deftest get-all-unique-tags
  (t/testing "empty tag list for most errors"
    (t/are [marks]
           (= [] (sut/get-all-unique-tags marks))
      nil
      []
      [nil]
      [{:other "thing"}]
      [{:tags nil}]))
  (t/is
   (= ["other" "thing"]
      (sut/get-all-unique-tags
       [{:tags [" thing " "thing"]}
        {:tags ["thing" "other"]}]))
   "unique tags trimmed and sorted")
  (t/is
   (= ["apples" "corn"]
      (sut/get-all-unique-tags
       [{:tags ["Apples" "corn"]}
        {:tags ["apples" "Corn"]}]))
   "unique tags should be all lowercase"))

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

(t/deftest mobile?-test
  (t/testing "mobile detection"
    (t/is (sut/mobile? #js {:userAgent "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X)"}))
    (t/is (sut/mobile? #js {:userAgent "Mozilla/5.0 (Linux; Android 10; SM-G981B)"}))
    (t/is (not (sut/mobile? #js {:userAgent "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})))
    (t/is (not (sut/mobile? nil)))))

(t/deftest make-map-link
  (t/testing "nil/empty cases"
    (t/is (nil? (sut/make-map-link nil nil)))
    (t/is (nil? (sut/make-map-link "" ""))))

  (t/testing "mobile (geo:)"
    (t/are [expected lat lon]
         (= expected (sut/make-map-link lat lon true))
      "geo:1,2" 1 2
      "geo:1,2" "1" "2"))

  (t/testing "desktop (google maps)"
    (t/are [expected lat lon]
         (= expected (sut/make-map-link lat lon false))
      "https://www.google.com/maps/search/?api=1&query=1,2" 1 2
      "https://www.google.com/maps/search/?api=1&query=1,2" "1" "2")))

(t/deftest mark-popup-html
  (t/testing "empty mark"
    (t/is (= "(no details)" (sut/mark-popup-html nil)))
    (t/is (= "(no details)" (sut/mark-popup-html {}))))

  (t/testing "simple mark"
    (t/is (= "<b>My Mark</b><br>"
             (sut/mark-popup-html {:name "My Mark"})))
    (t/is (= "<b>My Mark</b><br>Apples, Oranges<br>"
             (sut/mark-popup-html
               {:name "My Mark" :tags ["Apples" "Oranges"]}))))

  (t/testing "XSS sanitization"
    (t/is (not (str/includes? (sut/mark-popup-html
                              {:name "<script>alert(1)</script>"}) "<script>")))
    (t/is (str/includes? (sut/mark-popup-html
                         {:name "<script>alert(1)</script>"}) "&lt;script&gt;"))
    (t/is (str/includes? (sut/mark-popup-html
                         {:tags ["<b>bold</b>"]}) "&lt;b&gt;bold&lt;/b&gt;")))

  (t/testing "disabling fields"
    (let [mark {:name "My Mark" :tags ["Tag1"]}]
      (t/is (not (str/includes? (sut/mark-popup-html mark {:disable-name? true}) "My Mark")))
      (t/is (str/includes? (sut/mark-popup-html mark {:disable-name? true}) "Tag1"))

      (t/is (str/includes? (sut/mark-popup-html mark {:disable-tags? true}) "My Mark"))
      (t/is (not (str/includes? (sut/mark-popup-html mark {:disable-tags? true}) "Tag1")))

      (t/is (= "(no details)" (sut/mark-popup-html mark {:disable-name? true :disable-tags? true}))))))
