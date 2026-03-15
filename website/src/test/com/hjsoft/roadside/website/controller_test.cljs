(ns com.hjsoft.roadside.website.controller-test
  (:require [cljs.test :refer [deftest is testing async]]
            [com.hjsoft.roadside.website.controller :as sut]
            [com.hjsoft.roadside.website.storage :as storage]
            [com.hjsoft.roadside.website.api :as api]
            [clojure.string :as str]
            [cljs.core.async :refer [go]]
            [com.hjsoft.roadside.website.state :as state]))

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
  {:fetch-stands (fn [& _] (go {:success true :data []}))
   :create-stand (fn [& _] (go {:success true}))
   :update-stand (fn [& _] (go {:success true}))
   :delete-stand (fn [& _] (go {:success true}))
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
        (sut/save-local-data! ["stand1"] {:user "alice"} [10 20] 15)
        (is (= ["stand1"] (get @saved "roadside-stands")))
        (is (= {:user "alice"} (get @saved "roadside-settings")))
        (is (= [10 20] (get @saved "roadside-map-center")))
        (is (= 15 (get @saved "roadside-map-zoom")))))))

(deftest create-stand-test
  (async done
    (testing "create-stand! updates state and triggers remote creation"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            app-state {:settings {:user "alice" :password "secret"} :stands []}
            form-data {:name "New Stand" :coordinate "1.0, 2.0"}]
        (let [result (sut/create-stand! app-state dispatch form-data mock-deps)]
          (is (true? result))
          (is (some (fn [[type _]] (= type :set-stands)) @dispatched))
          (wait-for dispatched
                    (fn [actions] (some (fn [[type payload]]
                                          (and (= type :set-notification)
                                               (= (:type payload) :success)))
                                        actions))
                    done 1000))))))

(deftest update-stand-test
  (async done
    (testing "update-stand! replaces stand in state and triggers remote update"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            old-stand {:id "s1" :name "Old"}
            app-state {:settings {:user "alice"
                                  :password "secret"}
                       :stands [old-stand]}
            form-data {:id "s1"
                       :name "New"
                       :coordinate "3.0, 4.0"}]
        (let [result (sut/update-stand!
                       app-state
                       dispatch
                       form-data
                       old-stand
                       mock-deps)]
          (is (true? result))
          (wait-for dispatched
                    (fn [actions] (some (fn [[type payload]]
                                          (and (= type :set-notification)
                                               (= (:type payload) :success)))
                                          actions))
                    done 1000))))))

(deftest fetch-remote-stands-test
  (async done
    (testing "fetch-remote-stands! dispatches stands and success notification"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            app-state {:settings {:user "alice" :password "secret"}
                       :map-center [10 20]}
            deps (assoc
                   mock-deps
                   :fetch-stands (fn [& _]
                                   (go {:success true :data [{:id "s1"}]} )))]
        (sut/fetch-remote-stands! app-state dispatch deps)
        (wait-for dispatched
                  (fn [actions] (some (fn [[type payload]]
                                        (and (= type :set-notification)
                                             (= (:type payload) :success)))
                                      actions))
                  (fn []
                    (is (some #(= (first %) :set-stands) @dispatched))
                    (is (some #(= (first %) :set-is-synced) @dispatched))
                    (done))
                  1000)))))

(deftest fetch-remote-stands-failure-test
  (async done
    (testing "fetch-remote-stands! dispatches error notification on failure"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            app-state {:settings {:user "alice" :password "secret"}
                       :map-center [10 20]}
            deps (assoc
                   mock-deps
                   :fetch-stands (fn [& _]
                                   (go {:success false :error "API Down"} )))]
        (sut/fetch-remote-stands! app-state dispatch deps)
        (wait-for dispatched
                  (fn [actions] (some (fn [[type payload]]
                                        (and (= type :set-notification)
                                             (= (:type payload) :error)
                                             (= (:message payload) "Sync failed: API Down")))
                                      actions))
                  done 1000)))))

(deftest create-stand-failure-test
  (async done
    (testing "create-stand! handles remote failure with error notification"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            app-state {:settings {:user "alice" :password "secret"} :stands []}
            form-data {:name "New Stand" :coordinate "1.0, 2.0"}
            deps (assoc mock-deps
                        :create-stand (fn [& _] (go {:success false :error "Conflict"})))]
        (sut/create-stand! app-state dispatch form-data deps)
        (wait-for dispatched
                  (fn [actions] (some (fn [[type payload]]
                                        (and (= type :set-notification)
                                             (= (:type payload) :error)
                                             (str/includes? (:message payload) "Create failed: Conflict")))
                                      actions))
                  done 1000)))))

(deftest delete-stand-test
  (async done
    (testing "delete-stand! removes stand from state and calls remote delete"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            app-state {:settings {:user "alice" :password "secret"}}
            stand {:id "s1" :name "Target"}]
        (sut/delete-stand! app-state dispatch stand mock-deps)
        (is (some
              (fn [[type payload]]
                (and
                  (= type :remove-stand)
                  (= payload stand)))
              @dispatched))
        (wait-for dispatched
                  (fn [actions] (some (fn [[type payload]]
                                        (and (= type :set-notification)
                                             (= (:type payload) :success)))
                                      actions))
                  done 1000)))))

(deftest lookup-address-test
  (async done
    (testing "lookup-address! calls geocoding API and updates form"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            on-update-called (atom nil)
            on-update (fn [action] (reset! on-update-called action))
            app-state {:settings {:user "alice" :password "secret"}}
            address-data {:address "123 Main St"}]
        (sut/lookup-address!
          app-state
          dispatch
          on-update
          address-data
          mock-deps)
        (wait-for on-update-called
                  (fn [val] (some? val))
                  (fn []
                    (is (some #(= (first %) :set-map-center) @dispatched))
                    (is (= (first @on-update-called) :update-field))
                    (done))
                  1000)))))

(deftest reverse-lookup-test
  (async done
    (testing "reverse-lookup! calls reverse geocoding API and updates fields"
      (let [on-update-actions (atom [])
            on-update (fn [action] (swap! on-update-actions conj action))
            app-state {:settings {:user "alice" :password "secret"}}]
        (sut/reverse-lookup! app-state (fn [_]) on-update 1.0 2.0 mock-deps)
        (wait-for on-update-actions
                  (fn [actions] (>= (count actions) 3))
                  (fn []
                    (is (some
                          (fn [[_ [field value]]]
                            (= field :address)) @on-update-actions))
                    (done))
                  1000)))))
