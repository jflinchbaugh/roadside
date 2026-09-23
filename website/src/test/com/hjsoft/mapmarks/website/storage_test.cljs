(ns com.hjsoft.mapmarks.website.storage-test
  (:require [cljs.test :as t]
            [com.hjsoft.mapmarks.website.storage :as sut]))

(defn clear-storage-fixture [f]
  (when (exists? js/localStorage)
    (.clear js/localStorage))
  (f))

(t/use-fixtures :each clear-storage-fixture)

(t/deftest storage-operations-test
  (t/testing "set-item! and get-item with simple value"
    (sut/set-item! "test-key" "test-value")
    (t/is (= "test-value" (sut/get-item "test-key"))))

  (t/testing "set-item! and get-item with complex map"
    (let [data {:a 1 :b [1 2 3] :c "hello"}]
      (sut/set-item! "complex-key" data)
      (t/is (= data (sut/get-item "complex-key")))))

  (t/testing "remove-item! deletes the key"
    (sut/set-item! "to-delete" "value")
    (t/is (= "value" (sut/get-item "to-delete")))
    (sut/remove-item! "to-delete")
    (t/is (nil? (sut/get-item "to-delete"))))

  (t/testing "get-item returns nil for non-existent key"
    (t/is (nil? (sut/get-item "missing-key")))))
