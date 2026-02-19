(ns server.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [server.core :as core]))

(deftest ping-test
  (testing "Ping handler returns 200 pong"
    (let [response (core/ping-handler {})]
      (is (= 200 (:status response)))
      (is (= "\"pong\"" (:body response))))))
