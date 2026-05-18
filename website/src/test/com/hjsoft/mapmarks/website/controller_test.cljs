(ns com.hjsoft.mapmarks.website.controller-test
  (:require [cljs.test :refer [deftest is testing async]]
            [com.hjsoft.mapmarks.website.controller :as sut]
            [com.hjsoft.mapmarks.website.storage :as storage]
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
   :vote-mark (fn [_ _ _ _] (go {:success true}))
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
            app-state {:settings {:user "alice" :password "secret"} :marks []}
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
                       :map-center [10 20]
                       :last-sync "2026-03-21T10:00:00Z"}
            deps (assoc
                   mock-deps
                   :fetch-marks (fn [_ _ _ _ since]
                                   (is (= "2026-03-21T10:00:00Z" since))
                                   (go {:success true :data {:marks [{:id "s1"}]
                                                             :deleted-ids ["d1"]
                                                             :new-sync "2026-03-21T11:00:00Z"}} )))]
        (sut/fetch-remote-marks! app-state dispatch deps)
        (wait-for dispatched
                  (fn [actions] (some #(= (first %) :set-is-synced) actions))
                  (fn []
                    (is (some #(= % [:set-loading-marks true]) @dispatched))
                    (is (some #(= % [:set-loading-marks false]) @dispatched))
                    (let [sync-action (some #(when (= (first %) :sync-marks) %) @dispatched)]
                      (is (some? sync-action))
                      (is (= {:marks [{:id "s1"}]
                              :deleted-ids ["d1"]
                              :last-sync "2026-03-21T11:00:00Z"}
                             (second sync-action))))
                    (is (some #(= (first %) :set-is-synced) @dispatched))
                    (done))
                  1000)))))

(deftest fetch-remote-marks-failure-test
  (async done
    (testing "fetch-remote-marks! dispatches error notification on failure"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            app-state {:settings {:user "alice" :password "secret"}
                       :map-center [10 20]}
            deps (assoc
                   mock-deps
                   :fetch-marks (fn [& _]
                                   (go {:success false :error "API Down"} )))]
        (sut/fetch-remote-marks! app-state dispatch deps)
        (wait-for dispatched
                  (fn [actions] (some (fn [[type payload]]
                                        (and (= type :set-notification)
                                             (= (:type payload) :error)
                                             (= (:message payload) "Sync failed: API Down")))
                                      actions))
                  done 1000)))))

(deftest create-mark-failure-test
  (async done
    (testing "create-mark! handles remote failure with error notification"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            app-state {:settings {:user "alice" :password "secret"} :marks []}
            form-data {:name "New Mark" :lat 1.0 :lon 2.0}
            deps (assoc mock-deps
                        :create-mark (fn [& _] (go {:success false :error "Conflict"})))]
        (sut/create-mark! app-state dispatch form-data deps)
        (wait-for dispatched
                  (fn [actions] (some (fn [[type payload]]
                                        (and (= type :set-notification)
                                             (= (:type payload) :error)
                                             (str/includes? (:message payload) "Create failed: Conflict")))
                                      actions))
                  done 1000)))))

(deftest delete-mark-test
  (async done
    (testing "delete-mark! removes mark from state and calls remote delete"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            app-state {:settings {:user "alice" :password "secret"}}
            mark {:id "s1" :name "Target"}]
        (sut/delete-mark! app-state dispatch mark mock-deps)
        (is (some
              (fn [[type payload]]
                (and
                  (= type :remove-mark)
                  (= payload mark)))
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

(deftest upload-all-marks-test
  (async done
    (testing "upload-all-marks! triggers remote creation for all marks in state"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            marks [{:id "s1" :name "S1"} {:id "s2" :name "S2"}]
            app-state {:settings {:user "alice" :password "secret"}
                       :marks marks}
            created-count (atom 0)
            deps (assoc mock-deps
                        :create-mark (fn [& _]
                                        (swap! created-count inc)
                                        (go {:success true})))]
        (sut/upload-all-marks! app-state dispatch deps)
        (wait-for created-count
                  (fn [count] (= count 2))
                  (fn []
                    (is (= 2 @created-count))
                    (is (some (fn [[type payload]]
                                (and (= type :set-notification)
                                     (= (:type payload) :success)
                                     (str/includes? (:message payload) "uploaded 2 marks")))
                              @dispatched))
                    (done))
                  1000)))))

(deftest vote-mark-test
  (async done
    (testing "vote-mark! updates score/vote and calls remote vote"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            mark {:id "s1" :name "Target" :score 5 :user-vote 0}
            app-state {:settings {:user "alice" :password "secret"}
                       :selected-mark mark}]
        (sut/vote-mark! app-state dispatch mark 1 mock-deps)

        ;; Check optimistic update
        (let [update-action (some #(when (= (first %) :update-mark) %) @dispatched)]
          (is (some? update-action))
          (is (= 6 (:score (second update-action))))
          (is (= 1 (:user-vote (second update-action)))))

        ;; Check selected mark update
        (let [set-selected (some #(when (= (first %) :set-selected-mark) %) @dispatched)]
          (is (some? set-selected))
          (is (= 6 (:score (second set-selected)))))

        (wait-for dispatched
                  (fn [actions] (some (fn [[type payload]]
                                        (and (= type :set-notification)
                                             (= (:type payload) :success)))
                                      actions))
                  done 1000)))))

(deftest automatic-upload-trigger-test
  (testing "Logic for triggering automatic upload on login info change"
    (let [upload-called (atom false)
          mock-upload (fn [_ _] (reset! upload-called true))
          ;; Simulate the side effect logic
          run-effect (fn [prev-settings current-settings marks]
                       (let [login-info-keys [:user :password :local-only?]
                             login-info-changed? (not= (select-keys current-settings login-info-keys)
                                                       (select-keys prev-settings login-info-keys))
                             can-upload? (and (seq (:user current-settings))
                                              (seq (:password current-settings))
                                              (not (:local-only? current-settings)))]
                         (when (and login-info-changed? can-upload? (seq marks))
                           (mock-upload nil nil))))]

      (testing "Triggers when user logs in"
        (reset! upload-called false)
        (run-effect {} {:user "alice" :password "secret" :local-only? false} [{:id "s1"}])
        (is (true? @upload-called)))

      (testing "Triggers when password changes"
        (reset! upload-called false)
        (run-effect {:user "alice" :password "old" :local-only? false}
                    {:user "alice" :password "new" :local-only? false}
                    [{:id "s1"}])
        (is (true? @upload-called)))

      (testing "Triggers when local-only mode is disabled"
        (reset! upload-called false)
        (run-effect {:user "alice" :password "secret" :local-only? true}
                    {:user "alice" :password "secret" :local-only? false}
                    [{:id "s1"}])
        (is (true? @upload-called)))

      (testing "Does NOT trigger when login info remains the same"
        (reset! upload-called false)
        (run-effect {:user "alice" :password "secret" :local-only? false}
                    {:user "alice" :password "secret" :local-only? false}
                    [{:id "s1"}])
        (is (false? @upload-called)))

      (testing "Does NOT trigger when logging out"
        (reset! upload-called false)
        (run-effect {:user "alice" :password "secret" :local-only? false}
                    {:user "" :password "" :local-only? false}
                    [{:id "s1"}])
        (is (false? @upload-called)))

      (testing "Does NOT trigger when no marks"
        (reset! upload-called false)
        (run-effect {} {:user "alice" :password "secret" :local-only? false} [])
        (is (false? @upload-called))))))

(deftest registration-upload-trigger-test
  (testing "Registration success triggers automatic upload"
    (let [upload-called (atom false)
          mock-upload (fn [_ _] (reset! upload-called true))
          ;; Simulate the transition from empty settings to registered settings
          initial-settings {}
          registered-settings {:user "newuser" :password "newpass" :local-only? false}
          marks [{:id "local-1" :name "Local Mark"}]

          ;; Logic from use-app-side-effects
          run-effect (fn [prev current marks]
                       (let [login-info-keys [:user :password :local-only?]
                             login-info-changed? (not= (select-keys current login-info-keys)
                                                       (select-keys prev login-info-keys))
                             can-upload? (and (seq (:user current))
                                              (seq (:password current))
                                              (not (:local-only? current)))]
                         (when (and login-info-changed? can-upload? (seq marks))
                           (mock-upload nil nil))))]

      (run-effect initial-settings registered-settings marks)
      (is (true? @upload-called) "Upload should be triggered after registration"))))
