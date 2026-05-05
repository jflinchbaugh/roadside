(ns com.hjsoft.roadside.website.local-only-test
  (:require [cljs.test :refer [deftest is testing async]]
            [com.hjsoft.roadside.website.controller :as sut]
            [cljs.core.async :refer [go]]))

(def mock-deps
  {:fetch-stands (fn [& _] (go {:success true :data []}))
   :create-stand (fn [& _] (go {:success true}))
   :update-stand (fn [& _] (go {:success true}))
   :delete-stand (fn [& _] (go {:success true}))
   :geocode-address (fn [& _] (go {:success true :lat 1.0 :lng 2.0}))
   :reverse-geocode (fn [& _] (go {:success true
                                   :data {:address {:road "Main St"
                                                    :city "York"
                                                    :state "PA"}}}))})

(deftest fetch-remote-stands-local-only-test
  (testing "fetch-remote-stands! skips remote call when local-only is true"
    (let [dispatched (atom [])
          dispatch (fn [action] (swap! dispatched conj action))
          called (atom false)
          app-state {:settings {:user "alice" :password "secret" :local-only? true}
                     :map-center [10 20]}
          deps (assoc mock-deps
                      :fetch-stands (fn [& _]
                                      (reset! called true)
                                      (go {:success true :data []})))]
      (sut/fetch-remote-stands! app-state dispatch deps)
      (is (false? @called))
      (is (empty? @dispatched)))))

(deftest create-stand-local-only-test
  (testing "create-stand! updates local state but skips remote call when local-only is true"
    (let [dispatched (atom [])
          dispatch (fn [action] (swap! dispatched conj action))
          called (atom false)
          app-state {:settings {:user "alice" :password "secret" :local-only? true}
                     :stands []}
          form-data {:name "New Stand" :lat 1.0 :lon 2.0}
          deps (assoc mock-deps
                      :create-stand (fn [& _]
                                      (reset! called true)
                                      (go {:success true})))]
      (let [result (sut/create-stand! app-state dispatch form-data deps)]
        (is (true? result))
        (is (some (fn [[type _]] (= type :set-stands)) @dispatched))
        (is (false? @called))
        ;; Should NOT have a success notification for remote
        (is (not (some (fn [[type payload]]
                         (and (= type :set-notification)
                              (= (:type payload) :success)))
                       @dispatched)))))))

(deftest upload-all-stands-local-only-test
  (testing "upload-all-stands! shows error and skips upload when local-only is true"
    (let [dispatched (atom [])
          dispatch (fn [action] (swap! dispatched conj action))
          called (atom false)
          app-state {:settings {:user "alice" :password "secret" :local-only? true}
                     :stands [{:id "s1"}]}
          deps (assoc mock-deps
                      :create-stand (fn [& _]
                                      (reset! called true)
                                      (go {:success true})))]
      (sut/upload-all-stands! app-state dispatch deps)
      (is (false? @called))
      (is (some (fn [[type payload]]
                  (and (= type :set-notification)
                       (= (:type payload) :error)
                       (= (:message payload) "Remote operations disabled by settings")))
                @dispatched)))))

(deftest lookup-address-local-only-test
  (testing "lookup-address! shows error and skips remote call when local-only is true"
    (let [dispatched (atom [])
          dispatch (fn [action] (swap! dispatched conj action))
          called (atom false)
          app-state {:settings {:user "alice" :password "secret" :local-only? true}}
          deps (assoc mock-deps
                      :geocode-address (fn [& _]
                                         (reset! called true)
                                         (go {:success true})))]
      (sut/lookup-address! app-state dispatch (fn [_]) {:address "123 Main St"} deps)
      (is (false? @called))
      (is (some (fn [[type payload]]
                  (and (= type :set-notification)
                       (= (:type payload) :error)
                       (= (:message payload) "Remote operations disabled by settings")))
                @dispatched)))))
