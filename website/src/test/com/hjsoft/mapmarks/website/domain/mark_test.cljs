(ns com.hjsoft.mapmarks.website.domain.mark-test
  (:require [com.hjsoft.mapmarks.website.domain.mark :as sut]
            [cljs.test :refer [deftest is testing]]))

(deftest mark-key-test
  (testing "nil or empty mark"
    (is (nil? (sut/mark-key nil)))
    (is (= "|,||||" (sut/mark-key {}))))

  (testing "id-based key"
    (is (= "my-uuid" (sut/mark-key {:id "my-uuid"}))))

  (testing "content-based key"
    (is (= "name|1,2|address|town|state|prod,thing"
           (sut/mark-key {:name "name"
                           :lat 1.0
                           :lon 2.0
                           :address "address"
                           :town "town"
                           :state "state"
                           :tags ["prod" "thing"]})))))

(deftest infer-tags-test
  (let [all-tags ["apples" "corn" "peaches"]]
    (testing "detects tags from name"
      (is (= ["apples"] (sut/infer-tags "Fresh Apples" [] all-tags)))
      (is (= ["corn" "peaches"] (sut/infer-tags "Corn and Peaches" [] all-tags))))

    (testing "doesn't duplicate existing tags"
      (is (= ["apples"] (sut/infer-tags "Fresh Apples" ["Apples"] all-tags)))
      (is (= ["apples"] (sut/infer-tags "Fresh Apples" ["apples"] all-tags))
          "should not add Apples if apples already exists"))

    (testing "handles nil or empty names"
      (is (= [] (sut/infer-tags nil [] all-tags)))
      (is (= [] (sut/infer-tags "" [] all-tags))))))

(deftest init-form-state-test
  (testing "initializes with defaults when no editing-mark"
    (let [map-center [40.5 -76.5]
          state (sut/init-form-state {:map-center map-center})]
      (is (= "40.5, -76.5" (:coordinate state)))
      (is (= 40.5 (:lat state)))
      (is (= -76.5 (:lon state)))
      (is (= "" (:name state)))
      (is (false? (:show-address? state)))
      (is (= "" (:current-tag state)))))

  (testing "initializes from editing-mark and detects show-address?"
    (let [editing {:name "Existing" :address "123 Main St" :lat 1.0 :lon 2.0}
          state (sut/init-form-state {:editing-mark editing})]
      (is (= "Existing" (:name state)))
      (is (= "1, 2" (:coordinate state)))
      (is (= 1.0 (:lat state)))
      (is (= 2.0 (:lon state)))
      (is (true? (:show-address? state)))
      (is (= "" (:current-tag state))))))

(deftest mark-form-reducer-test
  (testing "update-field"
    (let [state {:name ""}
          next-state (sut/mark-form-reducer
                       state
                       [:update-field [:name "New Name"]])]
      (is (= "New Name" (:name next-state)))))

  (testing "update-current-tag"
    (let [state {:current-tag ""}
          next-state (sut/mark-form-reducer
                       state
                       [:update-current-tag "new tag"])]
      (is (= "new tag" (:current-tag next-state)))))

  (testing "add-tag"
    (let [state {:tags [] :current-tag "  Apples  "}
          state1 (sut/mark-form-reducer state [:add-tag])]
      (is (= ["apples"] (:tags state1)))
      (is (= "" (:current-tag state1)))))

  (testing "prevent duplicate tags"
    (let [state {:tags ["apples"] :current-tag "Apples"}
          next-state (sut/mark-form-reducer state [:add-tag])]
      (is (= ["apples"] (:tags next-state)))
      (is (= "" (:current-tag next-state))))
    (let [state {:tags ["apples"] :current-tag "apples"}
          next-state (sut/mark-form-reducer state [:add-tag])]
      (is (= ["apples"] (:tags next-state)))
      (is (= "" (:current-tag next-state)))))

  (testing "toggle-address"
    (let [state {:show-address? false}
          state1 (sut/mark-form-reducer state [:toggle-address])
          state2 (sut/mark-form-reducer state1 [:toggle-address])]
      (is (true? (:show-address? state1)))
      (is (false? (:show-address? state2)))))

  (testing "sync-coordinate and user-modified-coordinate?"
    (let [map-center [40.0 -76.0]
          state (sut/init-form-state {:map-center map-center})
          _ (is (false? (:user-modified-coordinate? state)) "Initially not modified")
          state1 (sut/mark-form-reducer state [:sync-coordinate "41.0, -77.0"])]
      (is (= "41.0, -77.0" (:coordinate state1)) "Coordinate synced when not modified")
      (is (false? (:user-modified-coordinate? state1)) "Still not modified after sync")
      (let [state2 (sut/mark-form-reducer state1 [:update-field [:coordinate "42.0, -78.0"]])]
        (is (true? (:user-modified-coordinate? state2)) "Marked as modified after update-field")
        (let [state3 (sut/mark-form-reducer state2 [:sync-coordinate "43.0, -79.0"])]
          (is (= "42.0, -78.0" (:coordinate state3)) "Coordinate NOT synced when modified"))))))

(deftest prepare-submit-data-test
  (testing "adds pending current tag"
    (let [state {:tags ["corn"]
                 :current-tag "Apples"}
          final (sut/prepare-submit-data state)]
      (is (= ["apples" "corn"] (:tags final)))
      (is (nil? (:current-tag final)))))

  (testing "empty current-tag adds nothing to tags"
    (let [state {:tags ["corn"]
                 :current-tag ""}
          final (sut/prepare-submit-data state)]
      (is (= ["corn"] (:tags final)))
      (is (nil? (:current-tag final)))))

  (testing "coordinates are parsed correctly"
    (let [state {:coordinate "40, -76"}
          final (sut/prepare-submit-data state)]
      (is (= 40 (:lat final)))
      (is (= -76 (:lon final)))
      (is (not (contains? final :coordinate))))))

(deftest add-and-edit-mark-test
  (let [marks [{:id "1"
                 :name "Apple Farm"
                 :tags ["apples"]
                 :lat 1.0
                 :lon 2.0
                 :site "test"}]]
    (testing "adding a new mark with auto-tag detection"
      (let [result (sut/add-mark
                    {:name "Better Apples"
                     :lat 3.0
                     :lon 4.0
                     :tags []
                     :site "test"}
                    marks
                    "test-user")]
        (is (:success result))
        (is (some #(= "apples" %) (:tags (:processed-data result)))
            "Automatically added apples
               because it was in the name and exists
               in other marks")
        (is (= "test-user" (:creator (:processed-data result))))))

    (testing "adding a mark with empty name"
      (let [result (sut/add-mark
                    {:name ""
                     :lat 3.0
                     :lon 4.0
                     :tags ["Apples"]
                     :site "test"}
                    marks
                    "test-user")]
        (is (:success result))
        (is (= "" (:name (:processed-data result))))))

    (testing "preventing duplicates in add-mark"
      (let [result (sut/add-mark
                    {:id "1" :name "Apple Farm" :lat 1.0 :lon 2.0 :tags ["apples"] :site "test"}
                    marks
                    "test-user")]
        (is (not (:success result)))
        (is (= "This mark already exists!" (:error result)))))

    (testing "editing mark replaces the old one and DOES NOT auto-detect tags"
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
        (is success)
        (is (:updated processed-data))
        (is (= {:id "1"
                :name "Original and corn"
                :lat 1.0
                :lon 2.0
                :tags ["apples"]
                :site "test"}
               (dissoc processed-data :updated :creator)))
        (is (not (some #(= "corn" %) (:tags processed-data)))
            "Should NOT have added corn even though it is in the name and exists elsewhere")
        (is (= [{:id "1"
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
