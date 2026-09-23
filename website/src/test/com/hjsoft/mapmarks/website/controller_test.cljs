(ns com.hjsoft.mapmarks.website.controller-test
  (:require [cljs.test :as t]
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
                    (t/is (pred @atom-ref) "Timeout waiting for condition")
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

(t/deftest save-local-data-test
  (t/testing "save-local-data! persists all provided fields to storage"
    (let [saved (atom {})
          site (:site config/config)]
      (with-redefs [storage/set-item! (fn [k v] (swap! saved assoc k v))]
        (sut/save-local-data! ["mark1"] {:user "alice"}
                              [10 20] 15 "2026-03-21T12:00:00Z")
        (t/is (= ["mark1"] (get @saved (str site "-marks"))))
        (t/is (= {:user "alice"} (get @saved (str site "-settings"))))
        (t/is (= [10 20] (get @saved (str site "-map-center"))))
        (t/is (= 15 (get @saved (str site "-map-zoom"))))
        (t/is (= "2026-03-21T12:00:00Z"
               (get @saved (str site "-last-sync"))))))))

(t/deftest create-mark-test
  (t/async done
    (t/testing "create-mark! updates state and triggers remote creation"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            app-state {:settings {:user "alice" :password "secret"} :config {:site "test"} :marks []}
            form-data {:name "New Mark" :lat 1.0 :lon 2.0}]
        (let [result (sut/create-mark! app-state dispatch form-data mock-deps)]
          (t/is (true? result))
          (t/is (some (fn [[type _]] (= type :set-marks)) @dispatched))
          (wait-for dispatched
                    (fn [actions] (some (fn [[type payload]]
                                          (and (= type :set-notification)
                                               (= (:type payload) :success)))
                                        actions))
                    done 1000))))))

(t/deftest update-mark-test
  (t/async done
    (t/testing "update-mark! replaces mark in state and triggers remote update"
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
          (t/is (true? result))
          (wait-for dispatched
                    (fn [actions] (some (fn [[type payload]]
                                          (and (= type :set-notification)
                                               (= (:type payload) :success)))
                                           actions))
                    done 1000))))))

(t/deftest fetch-remote-marks-test
  (t/async done
    (t/testing "fetch-remote-marks! dispatches sync-marks and loading states"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            app-state {:settings {:user "alice" :password "secret"}
                       :config {:site "test"}
                       :map-center [10 20]
                       :last-sync "2026-03-21T10:00:00Z"}
            deps (assoc
                   mock-deps
                   :fetch-marks (fn [site _ _ _ _ since]
                                  (t/is (= "test" site))
                                  (t/is (= "2026-03-21T10:00:00Z" since))
                                  (go {:success true
                                       :data {:marks [{:id "m1" :name "Remote Mark"}]
                                              :deleted-ids []
                                              :new-sync "2026-03-21T11:00:00Z"}})))]
        (sut/fetch-remote-marks! app-state dispatch deps)
        (t/is (some (fn [[type payload]] (and (= type :set-loading-marks) (true? payload))) @dispatched))
        (wait-for dispatched
                  (fn [actions] (some (fn [[type _]] (= type :sync-marks)) actions))
                  (fn []
                    (t/is (some (fn [[type payload]] (and (= type :set-loading-marks) (false? payload))) @dispatched))
                    (done))
                  1000)))))

(t/deftest delete-mark-test
  (t/async done
    (t/testing "delete-mark! removes from state and triggers remote delete"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            mark {:id "s1" :name "To Delete" :creator "alice"}
            app-state {:settings {:user "alice" :password "secret"}
                       :config {:site "test"}
                       :marks [mark]}
            deps (assoc mock-deps
                        :delete-mark (fn [site user pass id]
                                       (t/is (= "test" site))
                                       (t/is (= "s1" id))
                                       (go {:success true})))]
        (let [result (sut/delete-mark! app-state dispatch mark deps)]
          (t/is (true? result))
          (t/is (some (fn [[type payload]] (and (= type :remove-mark) (= (:id payload) "s1"))) @dispatched))
          (wait-for dispatched
                    (fn [actions] (some (fn [[type payload]]
                                          (and (= type :set-notification)
                                               (= (:type payload) :success)))
                                        actions))
                    done 1000))))))

(t/deftest vote-mark-test
  (t/async done
    (t/testing "vote-mark! updates state and triggers remote vote"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            mark {:id "m1" :name "Votable" :user-vote 0 :score 5}
            app-state {:settings {:user "alice" :password "secret"}
                       :config {:site "test"}
                       :selected-mark mark}
            deps (assoc mock-deps
                        :vote-mark (fn [site user pass id value]
                                     (t/is (= "test" site))
                                     (t/is (= "m1" id))
                                     (t/is (= 1 value))
                                     (go {:success true})))]
        (sut/vote-mark! app-state dispatch mark 1 deps)
        (t/is (some (fn [[type payload]] (and (= type :update-mark) (= (:user-vote payload) 1))) @dispatched))
        (wait-for dispatched
                  (fn [actions] (some (fn [[type payload]]
                                        (and (= type :set-notification)
                                             (= (:type payload) :success)))
                                      actions))
                  done 1000)))))

(t/deftest lookup-address-test
  (t/async done
    (t/testing "lookup-address! calls geocode and updates coordinate"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            updated-fields (atom [])
            on-update (fn [action] (swap! updated-fields conj action))
            app-state {:settings {:user "alice" :password "secret"}
                       :config {:site "test"}}
            address-data {:address "123 Main St"}
            deps (assoc mock-deps
                        :geocode-address (fn [site user pass addr]
                                           (t/is (= "test" site))
                                           (t/is (= "123 Main St" addr))
                                           (go {:success true :lat 10.0 :lng 20.0})))]
        (sut/lookup-address! app-state dispatch on-update address-data deps)
        (wait-for dispatched
                  (fn [actions] (some (fn [[type _]] (= type :set-map-center)) actions))
                  (fn []
                    (t/is (= [[:update-field [:coordinate "10, 20"]]] @updated-fields))
                    (done))
                  1000)))))

(t/deftest reverse-lookup-test
  (t/async done
    (t/testing "reverse-lookup! calls reverse-geocode and updates address fields"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            updated-fields (atom [])
            on-update (fn [action] (swap! updated-fields conj action))
            app-state {:settings {:user "alice" :password "secret"}
                       :config {:site "test"}}
            deps (assoc mock-deps
                        :reverse-geocode (fn [site user pass lat lon]
                                           (t/is (= "test" site))
                                           (t/is (= 40.0 lat))
                                           (t/is (= -76.0 lon))
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
                      (t/is (contains? updates [:update-field [:address "123 Main St"]]))
                      (t/is (contains? updates [:update-field [:town "Lancaster"]]))
                      (t/is (contains? updates [:update-field [:state "PA"]]))
                      (done)))
                  1000)))))

(t/deftest save-local-data-namespaced-test
  (t/testing "save-local-data! uses the configured site namespace"
    (let [saved (atom {})]
      (with-redefs [config/config (assoc config/config :site "potholes")
                    storage/set-item! (fn [k v] (swap! saved assoc k v))]
        (sut/save-local-data!
         ["mark1"]
         {:user "alice"}
         [10 20]
         15
         "2026-03-21T12:00:00Z")
        (t/is (= ["mark1"] (get @saved "potholes-marks")))
        (t/is (= {:user "alice"} (get @saved "potholes-settings")))
        (t/is (= [10 20] (get @saved "potholes-map-center")))
        (t/is (= 15 (get @saved "potholes-map-zoom")))
        (t/is (= "2026-03-21T12:00:00Z"
               (get @saved "potholes-last-sync")))))))

(defn- has-notif? [actions type pattern]
  (some (fn [[act-type payload]]
          (and (= act-type :set-notification)
               (= (:type payload) type)
               (str/includes? (:message payload) pattern)))
        actions))

(t/deftest notification-message-config-test
  (t/async done
    (t/testing "notifications use custom configured singular and plural names"
      (let [orig-config config/config
            cleanup (fn []
                      (set! config/config orig-config)
                      (done))
            dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            app-state {:settings {:user "alice" :password "secret"}
                       :config {:site "test"}
                       :marks [{:id "m1" :name "Old" :creator "bob"}]}
            editing-mark {:id "m1" :name "Old" :creator "bob"}
            form-data {:id "m1" :name "New" :lat 1.0 :lon 2.0}]
        (set! config/config (assoc config/config
                              :mark-name-singular "Pothole"
                              :mark-name-plural "Potholes"))
        ;; Test ownership warning on update (synchronous check)
        (t/is (false? (sut/update-mark!
                        app-state
                        dispatch
                        form-data
                        editing-mark
                        mock-deps)))
        (t/is (has-notif? @dispatched :error "pothole"))
        (swap! dispatched empty)

        ;; Test ownership warning on delete (synchronous check)
        (t/is (false? (sut/delete-mark!
                        app-state
                        dispatch
                        editing-mark
                        mock-deps)))
        (t/is (has-notif? @dispatched :error "pothole"))
        (swap! dispatched empty)

        ;; Test remote success message on create (async check)
        (sut/create-mark! (assoc app-state :marks [])
                          dispatch
                          {:name "New" :lat 1.0 :lon 2.0}
                          mock-deps)
        (wait-for dispatched
                  #(has-notif? % :success "Pothole added!")
                  (fn []
                    (swap! dispatched empty)
                    ;; Test upload-all-marks! (async check)
                    (let [marks [{:id "m2"
                                  :name "To Upload"
                                  :creator "alice"}]]
                      (sut/upload-all-marks!
                        (assoc app-state :marks marks)
                        dispatch
                        mock-deps)
                      (wait-for dispatched
                                (fn [acts]
                                  (and (has-notif? acts :info "potholes")
                                       (has-notif? acts :success "potholes")))
                                cleanup 1000)))
                  1000)))))

(t/deftest upload-all-marks-expiration-test
  (t/async done
    (t/testing "upload-all-marks! skips expired marks when show-expired? is false"
      (let [dispatched (atom [])
            dispatch (fn [action] (swap! dispatched conj action))
            uploaded (atom [])
            deps (assoc mock-deps
                        :create-mark (fn [_site _user _pass mark]
                                       (swap! uploaded conj mark)
                                       (go {:success true})))
            active-mark {:id "m1" :name "Active" :creator "alice"
                         :expiration "2099-01-01T00:00:00Z"}
            expired-mark {:id "m2" :name "Expired" :creator "alice"
                          :expiration "2020-01-01T00:00:00Z"}
            app-state {:settings {:user "alice" :password "secret"}
                       :config {:site "test"}
                       :show-expired? false
                       :marks [active-mark expired-mark]}]
        (sut/upload-all-marks! app-state dispatch deps)
        (wait-for uploaded
                  (fn [marks] (= 1 (count marks)))
                  (fn []
                    (t/is (= ["m1"] (mapv :id @uploaded)))
                    ;; Now test with show-expired? true
                    (reset! uploaded [])
                    (swap! dispatched empty)
                    (sut/upload-all-marks!
                     (assoc app-state :show-expired? true)
                     dispatch
                     deps)
                    (wait-for uploaded
                              (fn [marks] (= 2 (count marks)))
                              (fn []
                                (t/is (= ["m1" "m2"] (mapv :id @uploaded)))
                                (done))
                              1000))
                  1000)))))

(t/deftest extend-mark-test
  (t/testing "extend-mark! updates mark expiration date and notifies"
    (let [dispatched (atom [])
          dispatch (fn [action] (swap! dispatched conj action))
          mark {:id "m1" :name "Test Mark" :creator "alice"
                :lat 1.0 :lon 2.0 :site "test" :expiration "2026-08-01"}
          app-state {:settings {:user "alice" :password "secret"}
                     :config {:site "test" :default-expiration-days 30}
                     :marks [mark]}]
      (t/is (true? (sut/extend-mark! app-state dispatch mark 30 mock-deps)))
      (t/is (some (fn [[type payload]]
                  (and (= type :set-marks)
                       (some (fn [m]
                               (and (= (:id m) "m1")
                                    (not= (:expiration m) "2026-08-01")))
                             payload)))
                @dispatched)))))

(t/deftest extend-mark-review-mode-test
  (t/testing "review mode: extending mark that moves advances selected-mark"
    (let [dispatched (atom [])
          dispatch (fn [action] (swap! dispatched conj action))
          m1 {:id "m1" :name "Mark 1" :creator "alice"
              :lat 1.0 :lon 2.0 :site "test" :expiration "2026-08-01"}
          m2 {:id "m2" :name "Mark 2" :creator "alice"
              :lat 3.0 :lon 4.0 :site "test" :expiration "2026-08-15"}
          app-state {:settings {:user "alice" :password "secret"}
                     :config {:site "test" :default-expiration-days 30}
                     :review-mode? true
                     :selected-mark m1
                     :marks [m1 m2]}]
      (t/is (true? (sut/extend-mark! app-state dispatch m1 30 mock-deps)))
      (let [selected-actions (filter (fn [[type _]] (= type :set-selected-mark))
                                     @dispatched)]
        (t/is (= [[:set-selected-mark m2]] selected-actions)))))

  (t/testing "review mode: extending mark that does not move keeps it selected"
    (let [dispatched (atom [])
          dispatch (fn [action] (swap! dispatched conj action))
          m1 {:id "m1" :name "Mark 1" :creator "alice"
              :lat 1.0 :lon 2.0 :site "test" :expiration "2026-08-01"}
          m2 {:id "m2" :name "Mark 2" :creator "alice"
              :lat 3.0 :lon 4.0 :site "test" :expiration "2099-01-01"}
          app-state {:settings {:user "alice" :password "secret"}
                     :config {:site "test" :default-expiration-days 1}
                     :review-mode? true
                     :selected-mark m1
                     :marks [m1 m2]}]
      (t/is (true? (sut/extend-mark! app-state dispatch m1 1 mock-deps)))
      (let [selected-actions (filter (fn [[type _]] (= type :set-selected-mark))
                                     @dispatched)
            selected-mark (second (first selected-actions))]
        (t/is (= "m1" (:id selected-mark)))
        (t/is (not= "2026-08-01" (:expiration selected-mark)))))))

(t/deftest review-notification-test
  (let [created (atom [])
        mock-api (fn [title opts]
                   (swap! created conj
                          {:title title
                           :options (js->clj opts :keywordize-keys true)})
                   #js {:title title :options opts})]
    (set! (.-permission mock-api) "granted")

    (t/testing "sends browser notification when review is recommended"
      (reset! created [])
      (let [app-state {:marks [{:id "m1" :creator "alice"
                                :expiration "2026-08-01"}]
                       :settings {:user "alice"}
                       :config {:app-name "Roadside"
                                :mark-name-plural "Potholes"
                                :review-interval-days 30}
                       :last-reviewed "2026-01-01"}]
        (sut/notify-review-due! app-state mock-api)
        (t/is (= 1 (count @created)))
        (let [notif (first @created)]
          (t/is (= "Roadside" (:title notif)))
          (t/is (= "Some potholes are due to be reviewed."
                   (get-in notif [:options :body]))))))

    (t/testing "does not send browser notification when review is not due"
      (reset! created [])
      (let [app-state {:marks [{:id "m1" :creator "alice"
                                :expiration "2026-08-01"}]
                       :settings {:user "alice"}
                       :config {:app-name "Roadside"
                                :mark-name-plural "Potholes"
                                :review-interval-days 30}
                       :last-reviewed "2099-01-01"}]
        (sut/notify-review-due! app-state mock-api)
        (t/is (empty? @created))))

    (t/testing "requests permission when default, sends if granted"
      (reset! created [])
      (let [requested (atom false)
            default-api (fn [title opts]
                          (swap! created conj
                                 {:title title
                                  :options (js->clj opts :keywordize-keys true)})
                          #js {:title title :options opts})
            app-state {:marks [{:id "m1" :creator "alice"
                                :expiration "2026-08-01"}]
                       :settings {:user "alice"}
                       :config {:app-name "Roadside"
                                :mark-name-plural "Potholes"
                                :review-interval-days 30}
                       :last-reviewed "2026-01-01"}]
        (set! (.-permission default-api) "default")
        (set! (.-requestPermission default-api)
              (fn [cb]
                (reset! requested true)
                (cb "granted")))
        (sut/notify-review-due! app-state default-api)
        (t/is (true? @requested))
        (t/is (= 1 (count @created)))))

    (t/testing "does not send notification when permission is denied"
      (reset! created [])
      (let [denied-api (fn [title opts]
                         (swap! created conj {:title title})
                         #js {:title title :options opts})
            app-state {:marks [{:id "m1" :creator "alice"
                                :expiration "2026-08-01"}]
                       :settings {:user "alice"}
                       :config {:app-name "Roadside"
                                :mark-name-plural "Potholes"
                                :review-interval-days 30}
                       :last-reviewed "2026-01-01"}]
        (set! (.-permission denied-api) "denied")
        (sut/notify-review-due! app-state denied-api)
        (t/is (empty? @created))))))

(t/deftest sync-service-worker-review-state-test
  (let [messages (atom [])
        mock-controller #js {:postMessage
                             (fn [msg]
                               (swap! messages conj
                                      (js->clj msg :keywordize-keys true)))}
        mock-sw #js {:controller mock-controller}
        app-state {:marks [{:id "m1" :creator "alice"
                            :expiration "2026-08-01"}]
                   :settings {:user "alice"}
                   :config {:app-name "Roadside"
                            :mark-name-plural "Potholes"
                            :review-interval-days 30}
                   :last-reviewed "2026-01-01"}]
    (t/testing "syncs review config payload to service worker controller"
      (sut/sync-service-worker-review-state! app-state mock-sw)
      (t/is (= 1 (count @messages)))
      (let [msg (first @messages)]
        (t/is (= "SYNC_REVIEW_CONFIG" (:type msg)))
        (t/is (= "2026-01-01" (:lastReviewed msg)))
        (t/is (= 30 (:intervalDays msg)))
        (t/is (= true (:hasOwnedMarks msg)))
        (t/is (= "Roadside" (:appName msg)))
        (t/is (= "Potholes" (:markNamePlural msg)))))))
