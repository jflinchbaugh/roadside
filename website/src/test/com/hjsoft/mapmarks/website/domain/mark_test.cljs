(ns com.hjsoft.mapmarks.website.domain.mark-test
  (:require [com.hjsoft.mapmarks.website.domain.mark :as sut]
            [cljs.test :as t]))

(t/deftest mark-key-test
  (t/testing "nil or empty mark"
    (t/is (nil? (sut/mark-key nil)))
    (t/is (= "|,||||" (sut/mark-key {}))))

  (t/testing "id-based key"
    (t/is (= "my-uuid" (sut/mark-key {:id "my-uuid"}))))

  (t/testing "content-based key"
    (t/is (= "name|1,2|address|town|state|prod,thing"
           (sut/mark-key {:name "name"
                           :lat 1.0
                           :lon 2.0
                           :address "address"
                           :town "town"
                           :state "state"
                           :tags ["prod" "thing"]})))))

(t/deftest infer-tags-test
  (let [all-tags ["apples" "corn" "peaches"]]
    (t/testing "detects tags from name"
      (t/is (= ["apples"] (sut/infer-tags "Fresh Apples" [] all-tags)))
      (t/is (= ["corn" "peaches"] (sut/infer-tags "Corn and Peaches" [] all-tags))))

    (t/testing "doesn't duplicate existing tags"
      (t/is (= ["apples"] (sut/infer-tags "Fresh Apples" ["Apples"] all-tags)))
      (t/is (= ["apples"] (sut/infer-tags "Fresh Apples" ["apples"] all-tags))
          "should not add Apples if apples already exists"))

    (t/testing "handles nil or empty names"
      (t/is (= [] (sut/infer-tags nil [] all-tags)))
      (t/is (= [] (sut/infer-tags "" [] all-tags))))))

(t/deftest init-form-state-test
  (t/testing "initializes with defaults when no editing-mark"
    (let [map-center [40.5 -76.5]
          state (sut/init-form-state {:map-center map-center})]
      (t/is (= "40.5, -76.5" (:coordinate state)))
      (t/is (= 40.5 (:lat state)))
      (t/is (= -76.5 (:lon state)))
      (t/is (= "" (:name state)))
      (t/is (false? (:show-address? state)))
      (t/is (= "" (:current-tag state)))))

  (t/testing "initializes from editing-mark and detects show-address?"
    (let [editing {:name "Existing" :address "123 Main St" :lat 1.0 :lon 2.0}
          state (sut/init-form-state {:editing-mark editing})]
      (t/is (= "Existing" (:name state)))
      (t/is (= "1, 2" (:coordinate state)))
      (t/is (= 1.0 (:lat state)))
      (t/is (= 2.0 (:lon state)))
      (t/is (true? (:show-address? state)))
      (t/is (= "" (:current-tag state))))))

(t/deftest mark-form-reducer-test
  (t/testing "update-field"
    (let [state {:name ""}
          next-state (sut/mark-form-reducer
                       state
                       [:update-field [:name "New Name"]])]
      (t/is (= "New Name" (:name next-state)))))

  (t/testing "update-current-tag"
    (let [state {:current-tag ""}
          next-state (sut/mark-form-reducer
                       state
                       [:update-current-tag "new tag"])]
      (t/is (= "new tag" (:current-tag next-state)))))

  (t/testing "add-tag"
    (let [state {:tags [] :current-tag "  Apples  "}
          state1 (sut/mark-form-reducer state [:add-tag])]
      (t/is (= ["apples"] (:tags state1)))
      (t/is (= "" (:current-tag state1)))))

  (t/testing "prevent duplicate tags"
    (let [state {:tags ["apples"] :current-tag "Apples"}
          next-state (sut/mark-form-reducer state [:add-tag])]
      (t/is (= ["apples"] (:tags next-state)))
      (t/is (= "" (:current-tag next-state))))
    (let [state {:tags ["apples"] :current-tag "apples"}
          next-state (sut/mark-form-reducer state [:add-tag])]
      (t/is (= ["apples"] (:tags next-state)))
      (t/is (= "" (:current-tag next-state)))))

  (t/testing "toggle-address"
    (let [state {:show-address? false}
          state1 (sut/mark-form-reducer state [:toggle-address])
          state2 (sut/mark-form-reducer state1 [:toggle-address])]
      (t/is (true? (:show-address? state1)))
      (t/is (false? (:show-address? state2)))))

  (t/testing "sync-coordinate and user-modified-coordinate?"
    (let [map-center [40.0 -76.0]
          state (sut/init-form-state {:map-center map-center})
          _ (t/is (false? (:user-modified-coordinate? state)) "Initially not modified")
          state1 (sut/mark-form-reducer state [:sync-coordinate "41.0, -77.0"])]
      (t/is (= "41.0, -77.0" (:coordinate state1)) "Coordinate synced when not modified")
      (t/is (false? (:user-modified-coordinate? state1)) "Still not modified after sync")
      (let [state2 (sut/mark-form-reducer state1 [:update-field [:coordinate "42.0, -78.0"]])]
        (t/is (true? (:user-modified-coordinate? state2)) "Marked as modified after update-field")
        (let [state3 (sut/mark-form-reducer state2 [:sync-coordinate "43.0, -79.0"])]
          (t/is (= "42.0, -78.0" (:coordinate state3)) "Coordinate NOT synced when modified"))))))

(t/deftest prepare-submit-data-test
  (t/testing "adds pending current tag"
    (let [state {:tags ["corn"]
                 :current-tag "Apples"}
          final (sut/prepare-submit-data state)]
      (t/is (= ["apples" "corn"] (:tags final)))
      (t/is (nil? (:current-tag final)))))

  (t/testing "empty current-tag adds nothing to tags"
    (let [state {:tags ["corn"]
                 :current-tag ""}
          final (sut/prepare-submit-data state)]
      (t/is (= ["corn"] (:tags final)))
      (t/is (nil? (:current-tag final)))))

  (t/testing "coordinates are parsed correctly"
    (let [state {:coordinate "40, -76"}
          final (sut/prepare-submit-data state)]
      (t/is (= 40 (:lat final)))
      (t/is (= -76 (:lon final)))
      (t/is (not (contains? final :coordinate))))))

(t/deftest add-and-edit-mark-test
  (let [marks [{:id "1"
                 :name "Apple Farm"
                 :tags ["apples"]
                 :lat 1.0
                 :lon 2.0
                 :site "test"}]]
    (t/testing "adding a new mark with auto-tag detection"
      (let [result (sut/add-mark
                    {:name "Better Apples"
                     :lat 3.0
                     :lon 4.0
                     :tags []
                     :site "test"}
                    marks
                    "test-user")]
        (t/is (:success result))
        (t/is (some #(= "apples" %) (:tags (:processed-data result)))
            "Automatically added apples
               because it was in the name and exists
               in other marks")
        (t/is (= "test-user" (:creator (:processed-data result))))))

    (t/testing "adding a mark with empty name"
      (let [result (sut/add-mark
                    {:name ""
                     :lat 3.0
                     :lon 4.0
                     :tags ["Apples"]
                     :site "test"}
                    marks
                    "test-user")]
        (t/is (:success result))
        (t/is (= "" (:name (:processed-data result))))))

    (t/testing "preventing duplicates in add-mark"
      (let [result (sut/add-mark
                    {:id "1" :name "Apple Farm" :lat 1.0 :lon 2.0 :tags ["apples"] :site "test"}
                    marks
                    "test-user")]
        (t/is (not (:success result)))
        (t/is (= "This mark already exists!" (:error result)))))

    (t/testing "editing mark replaces the old one and DOES NOT auto-detect tags"
      (let [marks [{:id "1" :name "Original" :tags ["apples"] :lat 1.0 :lon 2.0 :site "test"}
                    {:id "2" :name "Corn Mark" :tags ["corn"] :lat 3.0 :lon 4.0 :site "test"}]
            result (sut/edit-mark
                    {:id "1"
                     :name "Original and corn"
                     :tags ["apples"]
                     :lat 1.0
                     :lon 2.0
                     :site "test"}
                    marks
                    (first marks)
                    "test-user")
            {:keys [success processed-data marks]} result]
        (t/is success)
        (t/is (:updated processed-data))
        (t/is (= {:id "1"
                :name "Original and corn"
                :lat 1.0
                :lon 2.0
                :tags ["apples"]
                :site "test"}
               (dissoc processed-data :updated :creator)))
        (t/is (not (some #(= "corn" %) (:tags processed-data)))
            "Should NOT have added corn even though it is in the name and exists elsewhere")
        (t/is (= [{:id "1"
                 :name "Original and corn"
                 :lat 1.0
                 :lon 2.0
                 :tags ["apples"]
                 :site "test"}
                {:id "2"
                 :name "Corn Mark"
                 :tags ["corn"]
                 :lat 3.0
                 :lon 4.0
                 :site "test"}]
               (map (fn [s] (dissoc s :updated :creator)) marks)))))))
 
(t/deftest review-marks-test
  (t/testing "mark-owner?"
    (t/is (true? (sut/mark-owner? {:creator "alice"} "alice")))
    (t/is (false? (sut/mark-owner? {:creator "alice"} "bob")))
    (t/is (true? (sut/mark-owner? {:creator ""} "alice")))
    (t/is (true? (sut/mark-owner? {:creator nil} "alice")))
    (t/is (true? (sut/mark-owner? {:creator nil} nil))))

  (t/testing "sort-marks-by-expiration sorts chronologically"
    (let [marks [{:id "1" :expiration "2026-10-15"}
                 {:id "2" :expiration "2026-08-01"}
                 {:id "3" :expiration nil}
                 {:id "4" :expiration "2026-09-15"}
                 {:id "5" :expiration ""}
                 {:id "6" :expiration "2026-09-23"}]
          sorted (sut/sort-marks-by-expiration marks)]
      (t/is (= ["2" "4" "6" "1" "3" "5"]
             (mapv :id sorted)))))

  (t/testing "extend-expiration"
    (let [extended (sut/extend-expiration "2026-08-01" 30)]
      (t/is (string? extended))
      (t/is (not= "2026-08-01" extended))))

  (t/testing "review-due?"
    (t/is (true? (sut/review-due? nil 30)))
    (t/is (true? (sut/review-due? "" 30)))
    (t/is (true? (sut/review-due? "2026-01-01" 30)))
    (t/is (false? (sut/review-due? "2099-01-01" 30))))

  (t/testing "review-recommended?"
    (t/is (false? (sut/review-recommended? {:marks [] :settings {:user "alice"}})))
    (t/is (true? (sut/review-recommended?
                {:marks [{:creator "alice"}]
                 :settings {:user "alice"}
                 :last-reviewed nil})))
    (t/is (false? (sut/review-recommended?
                 {:marks [{:creator "alice"}]
                  :settings {:user "alice"}
                  :last-reviewed "2099-01-01"})))))

(t/deftest next-review-mark-test
  (let [m1 {:id "1" :name "M1" :expiration "2026-08-01"}
        m2 {:id "2" :name "M2" :expiration "2026-08-15"}
        m3 {:id "3" :name "M3" :expiration "2026-09-01"}]
    (t/testing "returns next mark when mark moves in the list"
      (let [before [m1 m2 m3]
            after [m2 m3 (assoc m1 :expiration "2026-09-15")]]
        (t/is (= m2 (sut/next-review-mark before after "1")))))

    (t/testing "returns nil when mark does not move"
      (let [before [m1 m2 m3]
            after [(assoc m1 :expiration "2026-08-05") m2 m3]]
        (t/is (nil? (sut/next-review-mark before after "1")))))

    (t/testing "returns nil when mark is at the end and moves"
      (let [before [m1 m2]
            after [m1 (assoc m2 :expiration "2026-09-15")]]
        (t/is (nil? (sut/next-review-mark before after "2")))))

    (t/testing "returns nil when mark is not found"
      (t/is (nil? (sut/next-review-mark [m1 m2] [m1 m2] "999"))))))
