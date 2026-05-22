(ns com.hjsoft.mapmarks.website.controller-test
  (:require [cljs.test :refer [deftest is testing async]]
            [com.hjsoft.mapmarks.website.controller :as sut]
            [com.hjsoft.mapmarks.website.storage :as storage]
            [com.hjsoft.mapmarks.website.config :as config]
            [clojure.string :as str]
            [cljs.core.async :refer [go]]))

(defn wait-for [atom-ref pred done-fn timeout-ms]
  (let [start (.getTime (js/Date.))]
    (letfn [(check []
              (if (pred @atom-ref)
                (done-fn)
                (if (> (- (.getTime (js/Date.)) start) timeout-ms)
                  (do
                    (println "Wait-for timeout. Current state:"
                      (pr-str @atom-ref))
                    (is (pred @atom-ref) "Timeout waiting for condition")
                    (done-fn))
                  (js/setTimeout check 10))))]
      (check))))

(def mock-deps
  {:fetch-marks (fn [& _] (go {:success true :data []}))
   :create-mark (fn [& _] (go {:success true}))
   :update-mark (fn [& _] (go {:success true}))
   :delete-mark (fn [& _] (go {:success true}))
   :vote-mark (fn [& _] (go {:success true}))
   :geocode-address (fn [& _] (go {:success true :lat 1.0 :lng 2.0}))
   :reverse-geocode (fn [& _] (go {:success true
                                   :data {:address {:road
                                                    "Main St"
                                                    :city "York"
                                                    :state "PA"}}}))})

(deftest save-local-data-test
  (testing "save-local-data! persists all provided fields to storage"
    (let [saved (atom {})]
      (with-redefs [storage/set-item! (fn [k v] (swap! saved assoc k v))]
        (sut/save-local-data! ["mark1"] {:user "alice"} [10 20] 15 "2026-03-21T12:00:00Z")
        (is (= ["mark1"] (get @saved "mapmarks-marks")))
        (is (= {:user "alice"} (get @saved "mapmarks-settings")))
        (is (= [10 20] (get @saved "mapmarks-map-center")))
        (is (= 15 (get @saved "mapmarks-map-zoom")))
        (is (= "2026-03-21T12:00:00Z" (get @saved "mapmarks-last-sync")))))))

(deftest create-mark-test
  (async done
    (testing "create-mark! updates state and triggers remote creation"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            app-state {:settings {:user "alice" :password "secret"} :config {:site "test"} :marks []}
            form-data {:name "New Mark" :lat 1.0 :lon 2.0}]
        (let [result (sut/create-mark! app-state dispatch form-data mock-deps)]
          (is (true? result))
          (is (some (fn [[type _]] (= type :set-marks)) @dispatched))
          (wait-for dispatched
                    (fn [actions] (some (fn [[type payload]]
                                          (and (= type :set-notification)
                                               (= (:type payload) :success)))
                                        actions))
                    done 1000))))))

(deftest update-mark-test
  (async done
    (testing "update-mark! replaces mark in state and triggers remote update"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            old-mark {:id "s1" :name "Old" :lat 1.0 :lon 2.0}
            app-state {:settings {:user "alice"
                                  :password "secret"}
                       :config {:site "test"}
                       :marks [old-mark]}
            form-data {:id "s1"
                       :name "New"
                       :lat 3.0
                       :lon 4.0}]
        (let [result (sut/update-mark!
                       app-state
                       dispatch
                       form-data
                       old-mark
                       mock-deps)]
          (is (true? result))
          (wait-for dispatched
                    (fn [actions] (some (fn [[type payload]]
                                          (and (= type :set-notification)
                                               (= (:type payload) :success)))
                                          actions))
                    done 1000))))))

(deftest fetch-remote-marks-test
  (async done
    (testing "fetch-remote-marks! dispatches sync-marks and loading states"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            app-state {:settings {:user "alice" :password "secret"}
                       :config {:site "test"}
                       :map-center [10 20]
                       :last-sync "2026-03-21T10:00:00Z"}
            deps (assoc
                   mock-deps
                   :fetch-marks (fn [site _ _ _ _ since]
                                  (is (= "test" site))
                                  (is (= "2026-03-21T10:00:00Z" since))
                                  (go {:success true
                                       :data {:marks [{:id "m1" :name "Remote Mark"}]
                                              :deleted-ids []
                                              :new-sync "2026-03-21T11:00:00Z"}})))]
        (sut/fetch-remote-marks! app-state dispatch deps)
        (is (some (fn [[type payload]] (and (= type :set-loading-marks) (true? payload))) @dispatched))
        (wait-for dispatched
                  (fn [actions] (some (fn [[type _]] (= type :sync-marks)) actions))
                  (fn []
                    (is (some (fn [[type payload]] (and (= type :set-loading-marks) (false? payload))) @dispatched))
                    (done))
                  1000)))))

(deftest delete-mark-test
  (async done
    (testing "delete-mark! removes from state and triggers remote delete"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            mark {:id "s1" :name "To Delete" :creator "alice"}
            app-state {:settings {:user "alice" :password "secret"}
                       :config {:site "test"}
                       :marks [mark]}
            deps (assoc mock-deps
                        :delete-mark (fn [site user pass id]
                                       (is (= "test" site))
                                       (is (= "s1" id))
                                       (go {:success true})))]
        (let [result (sut/delete-mark! app-state dispatch mark deps)]
          (is (true? result))
          (is (some (fn [[type payload]] (and (= type :remove-mark) (= (:id payload) "s1"))) @dispatched))
          (wait-for dispatched
                    (fn [actions] (some (fn [[type payload]]
                                          (and (= type :set-notification)
                                               (= (:type payload) :success)))
                                        actions))
                    done 1000))))))

(deftest vote-mark-test
  (async done
    (testing "vote-mark! updates state and triggers remote vote"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            mark {:id "m1" :name "Votable" :user-vote 0 :score 5}
            app-state {:settings {:user "alice" :password "secret"}
                       :config {:site "test"}
                       :selected-mark mark}
            deps (assoc mock-deps
                        :vote-mark (fn [site user pass id value]
                                     (is (= "test" site))
                                     (is (= "m1" id))
                                     (is (= 1 value))
                                     (go {:success true})))]
        (sut/vote-mark! app-state dispatch mark 1 deps)
        (is (some (fn [[type payload]] (and (= type :update-mark) (= (:user-vote payload) 1))) @dispatched))
        (wait-for dispatched
                  (fn [actions] (some (fn [[type payload]]
                                        (and (= type :set-notification)
                                             (= (:type payload) :success)))
                                      actions))
                  done 1000)))))

(deftest lookup-address-test
  (async done
    (testing "lookup-address! calls geocode and updates coordinate"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            updated-fields (atom [])
            on-update (fn [action] (swap! updated-fields conj action))
            app-state {:settings {:user "alice" :password "secret"}
                       :config {:site "test"}}
            address-data {:address "123 Main St"}
            deps (assoc mock-deps
                        :geocode-address (fn [site user pass addr]
                                           (is (= "test" site))
                                           (is (= "123 Main St" addr))
                                           (go {:success true :lat 10.0 :lng 20.0})))]
        (sut/lookup-address! app-state dispatch on-update address-data deps)
        (wait-for dispatched
                  (fn [actions] (some (fn [[type _]] (= type :set-map-center)) actions))
                  (fn []
                    (is (= [[:update-field [:coordinate "10, 20"]]] @updated-fields))
                    (done))
                  1000)))))

(deftest reverse-lookup-test
  (async done
    (testing "reverse-lookup! calls reverse-geocode and updates address fields"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            updated-fields (atom [])
            on-update (fn [action] (swap! updated-fields conj action))
            app-state {:settings {:user "alice" :password "secret"}
                       :config {:site "test"}}
            deps (assoc mock-deps
                        :reverse-geocode (fn [site user pass lat lon]
                                           (is (= "test" site))
                                           (is (= 40.0 lat))
                                           (is (= -76.0 lon))
                                           (go {:success true
                                                :data {:address {:road "Main St"
                                                                 :house_number "123"
                                                                 :city "Lancaster"
                                                                 :state "PA"}}})))]
        (sut/reverse-lookup! app-state dispatch on-update 40.0 -76.0 deps)
        (wait-for dispatched
                  (fn [actions] (some (fn [[type _]] (= type :set-notification)) actions))
                  (fn []
                    (let [updates (set @updated-fields)]
                      (is (contains? updates [:update-field [:address "123 Main St"]]))
                      (is (contains? updates [:update-field [:town "Lancaster"]]))
                      (is (contains? updates [:update-field [:state "PA"]]))
                      (done)))
                  1000)))))

(deftest save-local-data-namespaced-test
  (testing "save-local-data! uses the configured site namespace"
    (let [saved (atom {})]
      (with-redefs [config/config (assoc config/config :site "potholes")
                    storage/set-item! (fn [k v] (swap! saved assoc k v))]
        (sut/save-local-data!
         ["mark1"]
         {:user "alice"}
         [10 20]
         15
         "2026-03-21T12:00:00Z")
        (is (= ["mark1"] (get @saved "potholes-marks")))
        (is (= {:user "alice"} (get @saved "potholes-settings")))
        (is (= [10 20] (get @saved "potholes-map-center")))
        (is (= 15 (get @saved "potholes-map-zoom")))
        (is (= "2026-03-21T12:00:00Z"
               (get @saved "potholes-last-sync")))))))
