(ns com.hjsoft.mapmarks.website.local-only-test
  (:require [cljs.test :as t]
            [com.hjsoft.mapmarks.website.controller :as sut]
            [cljs.core.async :refer [go]]))

(def mock-deps
  {:fetch-marks (fn [& _] (go {:success true :data []}))
   :create-mark (fn [& _] (go {:success true}))
   :update-mark (fn [& _] (go {:success true}))
   :delete-mark (fn [& _] (go {:success true}))
   :geocode-address (fn [& _] (go {:success true :lat 1.0 :lng 2.0}))
   :reverse-geocode (fn [& _] (go {:success true
                                   :data {:address {:road "Main St"
                                                    :city "York"
                                                    :state "PA"}}}))})

(t/deftest fetch-remote-marks-local-only-test
  (t/testing "fetch-remote-marks! skips remote call when local-only is true"
    (let [dispatched (atom [])
          dispatch (fn [action] (swap! dispatched conj action))
          called (atom false)
          app-state {:settings {:user "alice" :password "secret" :local-only? true}
                     :map-center [10 20]}
          deps (assoc mock-deps
                      :fetch-marks (fn [& _]
                                      (reset! called true)
                                      (go {:success true :data []})))]
      (sut/fetch-remote-marks! app-state dispatch deps)
      (t/is (false? @called))
      (t/is (empty? @dispatched)))))

(t/deftest create-mark-local-only-test
  (t/testing "create-mark! updates local state but skips remote call when local-only is true"
    (let [dispatched (atom [])
          dispatch (fn [action] (swap! dispatched conj action))
          called (atom false)
          app-state {:settings {:user "alice" :password "secret" :local-only? true}
                     :marks []
                     :config {:site "test-site"}}
          form-data {:name "New Mark" :lat 1.0 :lon 2.0 :site "test-site"}
          deps (assoc mock-deps
                      :create-mark (fn [& _]
                                      (reset! called true)
                                      (go {:success true})))]
      (let [result (sut/create-mark! app-state dispatch form-data deps)]
        (t/is (true? result))
        (t/is (some (fn [[type _]] (= type :set-marks)) @dispatched))
        (t/is (false? @called))
        ;; Should NOT have a success notification for remote
        (t/is (not (some (fn [[type payload]]
                         (and (= type :set-notification)
                              (= (:type payload) :success)))
                       @dispatched)))))))

(t/deftest upload-all-marks-local-only-test
  (t/testing "upload-all-marks! shows error and skips upload when local-only is true"
    (let [dispatched (atom [])
          dispatch (fn [action] (swap! dispatched conj action))
          called (atom false)
          app-state {:settings {:user "alice" :password "secret" :local-only? true}
                     :marks [{:id "s1"}]}
          deps (assoc mock-deps
                      :create-mark (fn [& _]
                                      (reset! called true)
                                      (go {:success true})))]
      (sut/upload-all-marks! app-state dispatch deps)
      (t/is (false? @called))
      (t/is (some (fn [[type payload]]
                  (and (= type :set-notification)
                       (= (:type payload) :error)
                       (= (:message payload) "Remote operations disabled by settings")))
                @dispatched)))))

(t/deftest lookup-address-local-only-test
  (t/testing "lookup-address! shows error and skips remote call when local-only is true"
    (let [dispatched (atom [])
          dispatch (fn [action] (swap! dispatched conj action))
          called (atom false)
          app-state {:settings {:user "alice" :password "secret" :local-only? true}}
          deps (assoc mock-deps
                      :geocode-address (fn [& _]
                                         (reset! called true)
                                         (go {:success true})))]
      (sut/lookup-address! app-state dispatch (fn [_]) {:address "123 Main St"} deps)
      (t/is (false? @called))
      (t/is (some (fn [[type payload]]
                  (and (= type :set-notification)
                       (= (:type payload) :error)
                       (= (:message payload) "Remote operations disabled by settings")))
                @dispatched)))))
