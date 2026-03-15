(ns com.hjsoft.roadside.website.storage-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [com.hjsoft.roadside.website.storage :as sut]))

(defn clear-storage-fixture [f]
  (when (exists? js/localStorage)
    (.clear js/localStorage))
  (f))

(use-fixtures :each clear-storage-fixture)

(deftest storage-operations-test
  (testing "set-item! and get-item with simple value"
    (sut/set-item! "test-key" "test-value")
    (is (= "test-value" (sut/get-item "test-key"))))

  (testing "set-item! and get-item with complex map"
    (let [data {:a 1 :b [1 2 3] :c "hello"}]
      (sut/set-item! "complex-key" data)
      (is (= data (sut/get-item "complex-key")))))

  (testing "remove-item! deletes the key"
    (sut/set-item! "to-delete" "value")
    (is (= "value" (sut/get-item "to-delete")))
    (sut/remove-item! "to-delete")
    (is (nil? (sut/get-item "to-delete"))))

  (testing "get-item returns nil for non-existent key"
    (is (nil? (sut/get-item "missing-key")))))
