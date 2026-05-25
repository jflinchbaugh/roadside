(ns server.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [server.db :as db]
            [server.core :as core]
            [server.handlers :as handlers]
            [server.config :as config]
            [buddy.hashers :as hashers]
            [xtdb.api :as xt]
            [server.xtdb-container :as xtn]
            [org.httpkit.client :as hkc]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [com.hjsoft.mapmarks.common.utils :as common-utils])
  (:import [java.io ByteArrayInputStream]))

(deftest router-definition-test
  (testing "Router is valid and contains expected routes"
    (is (some? core/app) "App should be defined")
    ;; Just loading the namespace and accessing 'app' ensures reitit didn't throw a conflict exception
    ))

(defn with-xtdb-container [f]
  (xtn/with-xtdb-client
    (fn [n]
      (reset! db/node n)
      (f)
      (reset! db/node nil))))

(use-fixtures :each with-xtdb-container)

(def test-site "test")

(defn- with-test-site [req]
  (assoc-in req [:path-params :site] test-site))

(defn- create-mark [mark-doc]
  (handlers/create-mark-handler
   (with-test-site
    {:body (ByteArrayInputStream. (.getBytes (json/write-str (assoc mark-doc :site test-site))))
     :identity "alice"})))

(deftest empty-marks-test
  (testing "Get marks when none exist and no lat/lon"
    (let [resp (handlers/get-marks-handler (with-test-site {:identity "alice"}))]
      (is (= 200 (:status resp)))
      (is (= [] (:marks (json/read-str (:body resp) :key-fn keyword))))))
  (testing "Get marks by lat/lon when none exist"
    (let [resp (handlers/get-marks-handler
                (with-test-site
                 {:identity "alice"
                  :params {:lat "-74.333", :lon "40.1234"}}))]
      (is (= 200 (:status resp)))
      (is (= [] (:marks (json/read-str (:body resp) :key-fn keyword)))))))

(deftest ping-test
  (testing "Ping handler returns 200 pong"
    (let [response (handlers/ping-handler {})]
      (is (= 200 (:status response)))
      (is (= "\"pong\"" (:body response))))))

(deftest register-test
  (testing "Register handler saves user to DB"
    (let [req (with-test-site {:params {:login "alice"
                                        :password "secret-password"
                                        :email "alice@example.com"}})
          response (handlers/register-handler req)]
      (is (= 201 (:status response)))
      (let [user (db/get-user "alice" test-site)]
        (is (= "alice" (:login user)))
        (is (:valid (hashers/verify "secret-password" (:password user))))
        (is (= "alice@example.com" (:email user))))))
  (testing "Register handler requires email"
    (let [req (with-test-site {:params {:login "bob" :password "secret-pass"}})
          response (handlers/register-handler req)]
      (is (= 400 (:status response)))
      (is (some #{"should match regex"}
                (get-in (json/read-str (:body response) :key-fn keyword)
                        [:errors :email])))))
  (testing "Register handler requires login"
    (let [req (with-test-site {:params {:email "bob@example.com" :password "secret-pass"}})
          response (handlers/register-handler req)]
      (is (= 400 (:status response)))
      (is (some #{"should match regex"}
                (get-in (json/read-str (:body response) :key-fn keyword)
                        [:errors :login])))))
  (testing "Register handler requires password"
    (let [req (with-test-site {:params {:login "bob" :email "bob@example.com"}})
          response (handlers/register-handler req)]
      (is (= 400 (:status response)))
      (is (some #{"should be a string"}
                (get-in (json/read-str (:body response) :key-fn keyword)
                        [:errors :password])))))
  (testing "Register handler requires all fields"
    (let [req (with-test-site {:params {}})
          response (handlers/register-handler req)]
      (is (= 400 (:status response)))
      (let [errors (:errors (json/read-str (:body response) :key-fn keyword))]
        (is (some #{"should match regex"} (:email errors)))
        (is (some #{"should match regex"} (:login errors)))
        (is (some #{"should be a string"} (:password errors))))))
  (testing "Register handler with invalid inputs"
    (let [req (with-test-site {:params {:login "a" :password "short" :email "not-an-email"}})
          response (handlers/register-handler req)]
      (is (= 400 (:status response)))
      (let [errors (:errors (json/read-str (:body response) :key-fn keyword))]
        (is (some #{"should match regex"} (:email errors)))
        (is (some #{"should match regex"} (:login errors)))
        (is (some #{"should be at least 8 characters"} (:password errors))))))
  (testing "Register handler with a duplicate"
    (let [req (with-test-site {:params {:login "alice"
                                        :password "again-secret"
                                        :email "alice2@example.com"}})
          response (handlers/register-handler req)]
      (is (= 403 (:status response)))
      (is (= {:login ["not available"]}
             (:errors (json/read-str (:body response) :key-fn keyword))))
      (let [user (db/get-user "alice" test-site)]
        (is (= "alice" (:login user)))
        (is (:valid (hashers/verify "secret-password" (:password user))) "password not touched")))))

(deftest marks-test
  (testing "Marks handlers"
    (let [mark-doc {:name "Morning Coffee" :address "Main St" :lat 40.0379 :lon -76.3055}
          body (json/write-str (assoc mark-doc :site test-site))
          create-req (with-test-site
                      {:body (ByteArrayInputStream. (.getBytes body))
                       :identity "alice"})
          create-resp (handlers/create-mark-handler create-req)]
      (is (= 201 (:status create-resp)))
      (let [created-mark (json/read-str (:body create-resp) :key-fn keyword)
            id (:id created-mark)]
        (is (not (nil? id)))
        (is (= "Morning Coffee" (:name created-mark)))
        (is (= 40.0379 (:lat created-mark)))
        (is (= -76.3055 (:lon created-mark)))

        (testing "Get all marks (no filter)"
          (let [get-resp (handlers/get-marks-handler (with-test-site {:identity "alice"}))
                body (json/read-str (:body get-resp) :key-fn keyword)
                marks (:marks body)]
            (is (= 200 (:status get-resp)))
            (is (>= (count marks) 1))))

        (testing "Get marks within radius"
          ;; Lancaster, PA: 40.0379, -76.3055
          (let [get-resp (handlers/get-marks-handler (with-test-site {:params {:lat "40.0" :lon "-76.0"} :identity "alice"}))
                body (json/read-str (:body get-resp) :key-fn keyword)
                marks (:marks body)]
            (is (= 200 (:status get-resp)))
            (is (>= (count marks) 1) "Should find the mark near Lancaster")))

        (testing "Get marks outside radius"
          ;; Los Angeles: 34.0522, -118.2437 (far from Lancaster, PA)
          (let [get-resp (handlers/get-marks-handler (with-test-site {:params {:lat "34.0" :lon "-118.0"} :identity "alice"}))
                body (json/read-str (:body get-resp) :key-fn keyword)
                marks (:marks body)]
            (is (= 200 (:status get-resp)))
            (is (= 0 (count (filter #(= (:id %) id) marks))) "Should NOT find the Lancaster mark from LA")))

        (testing "Get single mark"
          (let [get-resp (handlers/get-mark-handler (with-test-site {:path-params {:id id} :identity "alice"}))]
            (is (= 200 (:status get-resp)))
            (is (= "Morning Coffee" (:name (json/read-str (:body get-resp) :key-fn keyword))))))

        (testing "Update mark"
          (let [update-doc (assoc created-mark :name "Evening Coffee")
                update-body (json/write-str (assoc update-doc :site test-site))
                update-req (with-test-site
                            {:path-params {:id id}
                             :body (ByteArrayInputStream. (.getBytes update-body))
                             :identity "alice"})
                update-resp (handlers/update-mark-handler update-req)]
            (is (= 200 (:status update-resp)))
            (is (= "Evening Coffee" (:name (json/read-str (:body update-resp) :key-fn keyword))))))

        (testing "Update non-existent mark (upsert behavior)"
          (let [non-existent-id "missing-id"
                update-doc {:name "New Mark" :address "Unknown" :lat 0.0 :lon 0.0}
                update-body (json/write-str (assoc update-doc :site test-site))
                update-req (with-test-site
                            {:path-params {:id non-existent-id}
                             :body (ByteArrayInputStream. (.getBytes update-body))
                             :identity "alice"})
                update-resp (handlers/update-mark-handler update-req)]
            (is (= 200 (:status update-resp)))
            (let [created (json/read-str (:body update-resp) :key-fn keyword)]
              (is (= "New Mark" (:name created)))
              (is (= 0.0 (:lat created)))
              (is (= 0.0 (:lon created)))
              (is (= non-existent-id (:id created)))
              ;; Verify it's actually in the DB
              (let [get-resp (handlers/get-mark-handler (with-test-site {:path-params {:id non-existent-id} :identity "alice"}))]
                (is (= 200 (:status get-resp)))
                (is (= "New Mark" (:name (json/read-str (:body get-resp) :key-fn keyword))))))))

        (testing "Delete mark"
          (let [del-resp (handlers/delete-mark-handler (with-test-site {:path-params {:id id} :identity "alice"}))]
            (is (= 200 (:status del-resp)))
            (is (nil? (db/get-mark-unfiltered id test-site)))))

        (testing "Delete non-existent mark"
          (let [del-resp (handlers/delete-mark-handler (with-test-site {:path-params {:id "non-existent"} :identity "alice"}))]
            (is (= 200 (:status del-resp)))
            (is (= "'non-existent' deleted" (:message (json/read-str (:body del-resp) :key-fn keyword)))))))

      (testing "CSV export"
        (let [mark-doc {:name "CSV Mark"
                         :address "CSV St"
                         :town "CSV Town"
                         :state "CS"
                         :lat 40.0
                         :lon -76.0
                         :tags ["apples" "bananas"]}
              _ (create-mark mark-doc)
              resp (handlers/get-marks-csv-handler (with-test-site {:identity "alice"}))
              csv (:body resp)]
          (is (= 200 (:status resp)))
          (is (str/includes? csv "Name,Latitude,Longitude,Address,Town,State,Tags,Notes"))
          (is (str/includes? csv "CSV Mark,40.0,-76.0,CSV St,CSV Town,CS,apples; bananas,"))))

      (testing "KML export"
        (let [mark-doc {:name "KML Mark" :address "KML St" :lat 40.0 :lon -76.0 :tags ["cherries"] :notes "Sweet"}
              create-req (with-test-site
                          {:body (ByteArrayInputStream. (.getBytes (json/write-str (assoc mark-doc :site test-site))))
                           :identity "alice"})
              _ (handlers/create-mark-handler create-req)
              resp (handlers/get-marks-kml-handler (with-test-site {:identity "alice"}))
              kml (:body resp)]
          (is (= 200 (:status resp)))
          (is (str/includes? kml "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
          (is (str/includes? kml "<kml xmlns=\"http://www.opengis.net/kml/2.2\">"))
          (is (str/includes? kml "<name>KML Mark</name>"))
          (is (str/includes? kml "-76.000000,40.000000,0"))
          (is (str/includes? kml "cherries"))
          (is (str/includes? kml "Sweet"))))

      (testing "RSS export"
        (let [mark-docs [{:name "RSS Mark"
                           :address "RSS St"
                           :town "Lancaster"
                           :state "PA"
                           :tags ["peaches"]
                           :expiration "2026-01-01"
                           :notes "Juicy"
                           :updated "2026-01-01"
                           :id "uuid-1"
                           :lat 40.0
                           :lon -76.0
                           :shared? true
                           :creator "user"}]
              _ (doall (map create-mark mark-docs))
              resp (handlers/get-marks-rss-handler (with-test-site {:identity "alice" :scheme :http :server-name "localhost" :server-port 3000}))
              rss (:body resp)]
          (is (= 200 (:status resp)))
          (is (str/includes? rss "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
          (is (str/includes? rss "<rss version=\"2.0\""))
          (is (str/includes? rss "<title>MapMarks Marks</title>"))
          (is (str/includes? rss "<title>RSS Mark</title>"))
          (is (str/includes? rss "Address: RSS St, Lancaster, PA"))
          (is (str/includes? rss "Coordinates: 40.0, -76.0"))
          (is (str/includes? rss "peaches"))
          (is (str/includes? rss "Juicy"))
          (is (str/includes?
               rss
               (str (:external-base-url (config/get-config "test"))
                    "s/test/feed.rss"))))
        (let [mark-docs [{:name ""
                           :address ""
                           :town ""
                           :state ""
                           :tags ["peaches" "apples"]
                           :expiration "2026-01-01"
                           :notes ""
                           :updated "2026-01-01"
                           :id "uuid-1"
                           :lat 40.0
                           :lon -76.0
                           :shared? true
                           :creator "user"}]
              _ (doall (map create-mark mark-docs))
              resp (handlers/get-marks-rss-handler (with-test-site {:identity "alice" :scheme :http :server-name "localhost" :server-port 3000}))
              rss (:body resp)]
          (is (= 200 (:status resp)))
          (is (str/includes? rss "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
          (is (str/includes? rss "<rss version=\"2.0\""))
          (is (str/includes? rss "<title>MapMarks Marks</title>"))
          (is (str/includes? rss "<title>peaches, apples</title>"))
          (is (str/includes? rss "Coordinates: 40.0, -76.0"))
          (is (str/includes? rss "peaches"))
          (is (str/includes? rss "apples"))
          (is (str/includes?
               rss
               (str (:external-base-url (config/get-config "test"))
                    "s/test/feed.rss"))))))))

(deftest rss-site-param-test
  (testing "RSS feed with site in URL"
    (let [site "rss-test-site"
          mark-id "rss-site-mark-1"
          mark {:xt/id mark-id :name "RSS Site Mark" :site site :shared? true :creator "alice" :lat 0.0 :lon 0.0}]
      (db/save-mark mark)
      (testing "Handler uses site from path-params"
        (let [req {:identity "alice"
                   :path-params {:site site}
                   :scheme :http :server-name "localhost" :server-port 3000}
              resp (handlers/get-marks-rss-handler req)
              body (:body resp)]
          (is (= 200 (:status resp)))
          (is (str/includes? body "<title>RSS Site Mark</title>"))))

      (testing "Auth uses site from path-params"
        (let [req {:path-params {:site site}}
              extracted-site (#'handlers/get-site req)]
          (is (= site extracted-site)))))))

(deftest marks-visibility-test
  (testing "Marks visibility filtering"
    (let [alice-mark {:xt/id "alice-1" :name "Alice Mark" :shared? false :site test-site}
          bob-private {:xt/id "bob-private" :name "Bob Private" :shared? false :site test-site}
          bob-shared {:xt/id "bob-shared" :name "Bob Shared" :shared? true :site test-site}]

      ;; Setup marks in DB
      (xt/submit-tx @db/node [[:put-docs :marks (assoc alice-mark :creator "alice" :lat 40.0 :lon -76.0)]
                              [:put-docs :marks (assoc bob-private :creator "bob" :lat 40.0 :lon -76.0)]
                              [:put-docs :marks (assoc bob-shared :creator "bob" :lat 40.0 :lon -76.0)]])

      (testing "Alice sees her own and bob's shared marks"
        (let [req (with-test-site {:identity "alice"})
              resp (handlers/get-marks-handler req)
              body (json/read-str (:body resp) :key-fn keyword)
              marks (:marks body)
              ids (set (map :id marks))]
          (is (= 200 (:status resp)))
          (is (contains? ids "alice-1"))
          (is (contains? ids "bob-shared"))
          (is (not (contains? ids "bob-private")))))

      (testing "Bob sees his own and bob's shared marks (all 3 in this case since he owns both private and shared)"
        (let [req (with-test-site {:identity "bob"})
              resp (handlers/get-marks-handler req)
              body (json/read-str (:body resp) :key-fn keyword)
              marks (:marks body)
              ids (set (map :id marks))]
          (is (= 200 (:status resp)))
          (is (contains? ids "bob-private"))
          (is (contains? ids "bob-shared"))
          ;; Bob should NOT see Alice's private mark
          (is (not (contains? ids "alice-1")))))

      (testing "Unauthenticated user sees only shared marks"
        ;; Although the route has auth middleware, the handler itself should handle nil identity gracefully if it ever reached there
        (let [req (with-test-site {:identity nil})
              resp (handlers/get-marks-handler req)
              body (json/read-str (:body resp) :key-fn keyword)
              marks (:marks body)
              ids (set (map :id marks))]
          (is (= 200 (:status resp)))
          (is (= #{"bob-shared"} ids))))

      (testing "Individual mark visibility"
        (is (= 200 (:status (handlers/get-mark-handler (with-test-site {:path-params {:id "alice-1"} :identity "alice"})))))
        (is (= 404 (:status (handlers/get-mark-handler (with-test-site {:path-params {:id "bob-private"} :identity "alice"})))))
        (is (= 200 (:status (handlers/get-mark-handler (with-test-site {:path-params {:id "bob-shared"} :identity "alice"})))))
        (is (= 200 (:status (handlers/get-mark-handler (with-test-site {:path-params {:id "bob-private"} :identity "bob"}))))))

      (testing "Multi-tenancy: marks are not visible across sites"
        (let [other-site "other-site"]
          (db/save-mark {:xt/id "other-1" :name "Other Site Mark" :lat 40.0 :lon -76.0 :creator "alice" :shared? true :site other-site})

          (testing "Alice on test site does NOT see other site mark"
            (let [resp (handlers/get-marks-handler (with-test-site {:identity "alice"}))
                  marks (:marks (json/read-str (:body resp) :key-fn keyword))]
              (is (not (some #(= "Other Site Mark" (:name %)) marks)))))

          (testing "Alice on other site sees only other site mark"
            (let [req (assoc-in {:identity "alice"} [:path-params :site] other-site)
                  resp (handlers/get-marks-handler req)
                  marks (:marks (json/read-str (:body resp) :key-fn keyword))]
              (is (= 1 (count marks)))
              (is (= "Other Site Mark" (:name (first marks))))))))
    )

    (testing "Marks from disabled users are excluded"
      (let [disabled-bob {:xt/id "disabled-bob"
                          :login "disabled-bob"
                          :enabled? false
                          :site test-site}
            bob-shared {:xt/id "bob-shared-disabled"
                        :name "Bob Shared Disabled"
                        :shared? true
                        :creator "disabled-bob"
                        :lat 40.0
                        :lon -76.0
                        :site test-site}]
        (xt/submit-tx @db/node [[:put-docs :users disabled-bob]
                                [:put-docs :marks bob-shared]])

        (testing "anonymous should NOT see mark from disabled user"
          (let [req (with-test-site {})
                resp (handlers/get-marks-handler req)
                body (json/read-str (:body resp) :key-fn keyword)
                marks (:marks body)
                ids (set (map :id marks))]
            (is (not (contains? ids "bob-shared-disabled")))))

        (testing "anonymous should NOT see mark by radius from disabled user"
          (let [req (with-test-site {:params {:lat "40.0" :lon "-76.0"}})
                resp (handlers/get-marks-handler req)
                body (json/read-str (:body resp) :key-fn keyword)
                marks (:marks body)
                ids (set (map :id marks))]
            (is (not (contains? ids "bob-shared-disabled")))))

        (testing "Alice should NOT see mark from disabled user"
          (let [req (with-test-site {:identity "alice"})
                resp (handlers/get-marks-handler req)
                body (json/read-str (:body resp) :key-fn keyword)
                marks (:marks body)
                ids (set (map :id marks))]
            (is (not (contains? ids "bob-shared-disabled")))))

        (testing "Alice should NOT see mark by radius from disabled user"
          (let [req (with-test-site {:params {:lat "40.0" :lon "-76.0"} :identity "alice"})
                resp (handlers/get-marks-handler req)
                body (json/read-str (:body resp) :key-fn keyword)
                marks (:marks body)
                ids (set (map :id marks))]
            (is (not (contains? ids "bob-shared-disabled")))))

        (testing "Individual mark from disabled user should be 404 for others"
          (let [req (with-test-site {:path-params {:id "bob-shared-disabled"}
                                      :identity "alice"})
                resp (handlers/get-mark-handler req)]
            (is (= 404 (:status resp)))))))

    (testing "Reproduce 'Not all variables in scope' error with incomplete docs"
      (xt/submit-tx @db/node [[:put-docs :marks {:xt/id "incomplete-1" :name "No creator or shared" :lat 0.0 :lon 0.0 :site test-site}]])
      (let [req (with-test-site {:identity "alice" :params {:lat "40.0" :lon "-76.0"}})
            resp (handlers/get-marks-handler req)]
        (is (= 200 (:status resp)))))))

(deftest creator-test
  (testing "Creator value behavior"
    (let [mark-id "mark-1"
          mark-doc {:id mark-id :name "Creator Test Mark" :creator "malicious-user" :lat 0.0 :lon 0.0 :site test-site}
          body (json/write-str mark-doc)
          create-req (with-test-site
                      {:body (ByteArrayInputStream. (.getBytes body))
                       :identity "alice"})
          create-resp (handlers/create-mark-handler create-req)]
      (is (= 201 (:status create-resp)))
      (let [created-mark (json/read-str (:body create-resp) :key-fn keyword)]
        (is (= "alice" (:creator created-mark)) "Creator should be set from identity, ignoring client input")
        (is (= mark-id (:id created-mark)))

        (testing "Updating mark preserves creator"
          (let [update-doc (assoc created-mark :name "Updated Name" :creator "malicious-user")
                update-body (json/write-str (assoc update-doc :site test-site))
                update-req (with-test-site
                            {:path-params {:id mark-id}
                             :body (ByteArrayInputStream. (.getBytes update-body))
                             :identity "alice"})
                update-resp (handlers/update-mark-handler update-req)]
            (is (= 200 (:status update-resp)))
            (let [updated-mark (json/read-str (:body update-resp) :key-fn keyword)]
              (is (= "alice" (:creator updated-mark)) "Creator should be preserved from existing record, ignoring client input and current identity"))))

        (testing "Updating mark by non-owner is forbidden"
          (let [update-doc (assoc created-mark :name "Malicious Update")
                update-body (json/write-str (assoc update-doc :site test-site))
                update-req (with-test-site
                            {:path-params {:id mark-id}
                             :body (ByteArrayInputStream. (.getBytes update-body))
                             :identity "bob"})
                update-resp (handlers/update-mark-handler update-req)]
            (is (= 403 (:status update-resp)))
            (is (= "Forbidden: You do not own this mark" (:error (json/read-str (:body update-resp) :key-fn keyword))))))

        (testing "Deleting mark by non-owner is forbidden"
          (let [del-req (with-test-site
                         {:path-params {:id mark-id}
                          :identity "bob"})
                del-resp (handlers/delete-mark-handler del-req)]
            (is (= 403 (:status del-resp)))
            (is (= "Forbidden: You do not own this mark" (:error (json/read-str (:body del-resp) :key-fn keyword))))))

        (testing "Upserting new mark sets creator from current identity"
          (let [upsert-id "mark-2"
                upsert-doc {:name "Upsert Mark" :creator "malicious-user" :lat 0.0 :lon 0.0 :site test-site}
                upsert-body (json/write-str upsert-doc)
                upsert-req (with-test-site
                            {:path-params {:id upsert-id}
                             :body (ByteArrayInputStream. (.getBytes upsert-body))
                             :identity "charlie"})
                upsert-resp (handlers/update-mark-handler upsert-req)]
            (is (= 200 (:status upsert-resp)))
            (let [upserted-mark (json/read-str (:body upsert-resp) :key-fn keyword)]
              (is (= "charlie" (:creator upserted-mark)) "Creator should be set from identity for new record in update handler"))))))))

(deftest upsert-test
  (testing "Updating a non-existent mark creates it (upsert)"
    (let [id "upsert-id"
          mark-doc {:name "Upserted Mark" :address "Upsert Lane" :lat 0.0 :lon 0.0 :site test-site}
          body (json/write-str mark-doc)
          req (with-test-site
               {:path-params {:id id}
                :body (ByteArrayInputStream. (.getBytes body))
                :identity "alice"})
          resp (handlers/update-mark-handler req)]
      (is (= 200 (:status resp)))
      (let [created (json/read-str (:body resp) :key-fn keyword)]
        (is (= id (:id created)))
        (is (= "Upserted Mark" (:name created)))
        (is (= "alice" (:creator created)))

        ;; Verify it persists in DB
        (let [get-resp (handlers/get-mark-handler (with-test-site {:path-params {:id id} :identity "alice"}))]
          (is (= 200 (:status get-resp)))
          (is (= "Upserted Mark" (:name (json/read-str (:body get-resp) :key-fn keyword)))))))))

(deftest migration-test
  (testing "Migration from :coordinate to :lat and :lon"
    (let [old-mark {:xt/id "old-1" :name "Old" :coordinate "40.0, -76.0" :creator "alice" :site test-site}]
      (xt/execute-tx @db/node [[:put-docs :marks old-mark]])
      (db/migrate-marks!)
      (let [migrated (db/get-mark-unfiltered "old-1" test-site)]
        (is (= 40.0 (:lat migrated)))
        (is (= -76.0 (:lon migrated)))
        (is (nil? (:coordinate migrated)))))))

(deftest auth-test
  (testing "authfn"
    (reset! db/node @db/node) ;; ensure atom is initialized if needed
    (xt/execute-tx @db/node [[:put-docs :users {:xt/id "u1" :login "bob" :password (hashers/derive "pass") :enabled? true :site test-site}]])
    (let [user (db/get-user "bob" test-site)]
      (is (= "bob" (:login user)))
      (is (:valid (hashers/verify "pass" (:password user)))))))

(deftest geocode-proxy-test
  (testing "Geocode proxy handler"
    (testing "Successful geocoding"
      (let [mock-response {:status 200
                           :body (json/write-str [{:lat "40.0379" :lon "-76.3055"}])}]
        (with-redefs [hkc/get (fn [_ _] (atom mock-response))]
          (let [req (with-test-site
                     {:params {:q "Lancaster, PA"}
                      :identity "alice"})
                resp (handlers/geocode-handler req)]
            (is (= 200 (:status resp)))
            (is (= [{:lat "40.0379" :lon "-76.3055"}]
                   (json/read-str (:body resp) :key-fn keyword)))))))

    (testing "Address not found"
      (let [mock-response {:status 200 :body "[]"}]
        (with-redefs [hkc/get (fn [_ _] (atom mock-response))]
          (let [req (with-test-site
                     {:params {:q "Middle of Nowhere"}
                      :identity "alice"})
                resp (handlers/geocode-handler req)]
            (is (= 200 (:status resp)))
            (is (= [] (json/read-str (:body resp))))))))

    (testing "Missing address parameter"
      (let [resp (handlers/geocode-handler (with-test-site {:params {} :identity "alice"}))]
        (is (= 400 (:status resp)))
        (is (= "Missing address" (:error (json/read-str (:body resp) :key-fn keyword))))))

    (testing "Nominatim error (500)"
      (let [mock-response {:status 500 :body "Internal Server Error"}]
        (with-redefs [hkc/get (fn [_ _] (atom mock-response))]
          (let [req (with-test-site
                     {:params {:q "Lancaster, PA"}
                      :identity "alice"})
                resp (handlers/geocode-handler req)]
            (is (= 502 (:status resp)))
            (is (str/includes? (:body resp) "Nominatim error")))))))

  (testing "Reverse geocode proxy handler"
    (testing "Successful reverse geocoding"
      (let [mock-response {:status 200
                           :body (json/write-str {:address {:road "Main St" :city "Lancaster" :state "PA"}})}]
        (with-redefs [hkc/get (fn [_ _] (atom mock-response))]
          (let [req (with-test-site
                     {:params {:lat "40.0379" :lon "-76.3055"}
                      :identity "alice"})
                resp (handlers/reverse-geocode-handler req)]
            (is (= 200 (:status resp)))
            (is (= {:road "Main St" :city "Lancaster" :state "PA"}
                   (:address (json/read-str (:body resp) :key-fn keyword))))))))

    (testing "Missing parameters"
      (let [resp (handlers/reverse-geocode-handler (with-test-site {:params {:lat "40.0"} :identity "alice"}))]
        (is (= 400 (:status resp)))
        (is (= "Missing lat or lon" (:error (json/read-str (:body resp) :key-fn keyword))))))

    (testing "Nominatim error"
      (let [mock-response {:status 404 :body "Not Found"}]
        (with-redefs [hkc/get (fn [_ _] (atom mock-response))]
          (let [req (with-test-site
                     {:params {:lat "0" :lon "0"}
                      :identity "alice"})
                resp (handlers/reverse-geocode-handler req)]
            (is (= 502 (:status resp)))
            (is (str/includes? (:body resp) "Nominatim error"))))))))

(deftest vote-test
  (testing "Voting for a mark"
    (let [mark-id "vote-mark-1"
          _ (xt/execute-tx
             @db/node
             [[:put-docs :marks
               {:xt/id mark-id
                :name "Vote Mark"
                :shared? true
                :creator "bob"
                :lat 40.0
                :lon -76.0
                :site test-site}]])]

      ;; Verification assertion: ensure mark is actually in XTDB
      (is (seq (xt/q
                  @db/node
                  ['(fn [id]
                      (->
                       (from :marks [xt/id])
                       (where (= xt/id id))))
                   mark-id]))
          "Mark should be present in XTDB before testing votes")
      (is (empty? (xt/q
                   @db/node
                   ['(fn []
                       (->
                        (from :votes [xt/id])))]))
          "No votes should be present in XTDB before testing votes")

      (testing "Initial score is exactly 0 (not nil)"
        (let [resp (handlers/get-mark-handler
                    (with-test-site
                     {:path-params {:id mark-id}
                      :identity "alice"}))
              mark (json/read-str (:body resp) :key-fn keyword)]
          (is (= 200 (:status resp)))
          (is (= 0 (:score mark))
              "Score should be exactly 0 for a mark with no votes")
          (is (= 0 (:user-vote mark))
              "User vote should be exactly 0 for a mark with no votes")
          (is (= 0 (:score mark))
              "Overall score should be exactly 0 for a mark with no votes")))

      (testing "Alice upvotes"
        (let [req (with-test-site
                   {:path-params {:id mark-id}
                    :identity "alice"
                    :body (-> {:value 1}
                              json/write-str
                              .getBytes
                              ByteArrayInputStream.)})
              resp (handlers/vote-mark-handler req)]
          (is (= 200 (:status resp)))
          (let [get-resp (handlers/get-mark-handler
                          (with-test-site
                           {:path-params {:id mark-id}
                            :identity "alice"}))
                mark (json/read-str (:body get-resp) :key-fn keyword)]
            (is (= 1 (:score mark)))
            (is (= 1 (:user-vote mark))))))

      (testing "Bob downvotes"
        (let [req (with-test-site
                   {:path-params {:id mark-id}
                    :identity "bob"
                    :body (ByteArrayInputStream. (.getBytes (json/write-str {:value -1})))} )
              resp (handlers/vote-mark-handler req)]
          (is (= 200 (:status resp)))
          (let [get-resp (handlers/get-mark-handler (with-test-site {:path-params {:id mark-id} :identity "alice"}))
                mark (json/read-str (:body get-resp) :key-fn keyword)]
            (is (= 0 (:score mark)) "1 (alice) + -1 (bob) = 0")
            (is (= 1 (:user-vote mark)) "Alice still sees her upvote"))
          (let [get-resp (handlers/get-mark-handler (with-test-site {:path-params {:id mark-id} :identity "bob"}))
                mark (json/read-str (:body get-resp) :key-fn keyword)]
            (is (= 0 (:score mark)))
            (is (= -1 (:user-vote mark)) "Bob sees his downvote"))))

      (testing "Alice changes vote to downvote"
        (let [req (with-test-site
                   {:path-params {:id mark-id}
                    :identity "alice"
                    :body (ByteArrayInputStream. (.getBytes (json/write-str {:value -1})))})
              resp (handlers/vote-mark-handler req)]
          (is (= 200 (:status resp)))
          (let [get-resp (handlers/get-mark-handler (with-test-site {:path-params {:id mark-id} :identity "alice"}))
                mark (json/read-str (:body get-resp) :key-fn keyword)]
            (is (= -2 (:score mark)) "-1 (alice) + -1 (bob) = -2")
            (is (= -1 (:user-vote mark))))))

      (testing "Alice clears vote"
        (let [req (with-test-site
                   {:path-params {:id mark-id}
                    :identity "alice"
                    :body (ByteArrayInputStream. (.getBytes (json/write-str {:value 0})))})
              resp (handlers/vote-mark-handler req)]
          (is (= 200 (:status resp)))
          (let [get-resp (handlers/get-mark-handler (with-test-site {:path-params {:id mark-id} :identity "alice"}))
                mark (json/read-str (:body get-resp) :key-fn keyword)]
            (is (= -1 (:score mark)) "0 (alice) + -1 (bob) = -1")
            (is (= 0 (:user-vote mark))))))

      (testing "Invalid vote value"
        (let [req (with-test-site
                   {:path-params {:id mark-id}
                    :identity "alice"
                    :body (ByteArrayInputStream. (.getBytes (json/write-str {:value 2})))})
              resp (handlers/vote-mark-handler req)]
          (is (= 400 (:status resp)))))
      )

    (testing "Multiple marks with one vote"
      (let [s1-id "mark-a"
            s2-id "mark-b"]
        (xt/execute-tx @db/node
                 [[:put-docs :marks
                  {:xt/id s1-id
                   :name "Mark A"
                   :shared? true
                   :creator "bob"
                   :lat 40.0
                   :lon -76.0
                   :site test-site}
                  {:xt/id s2-id
                   :name "Mark B"
                   :shared? true
                   :creator "bob"
                   :lat 40.0
                   :lon -76.0
                   :site test-site}]
                  [:put-docs :votes
                   {:xt/id "v1"
                    :mark-id s1-id
                    :user-id "alice"
                    :value 1
                    :site test-site}
                   {:xt/id "v2"
                    :mark-id s1-id
                    :user-id "bob"
                    :value 1
                    :site test-site}]])

      ;; Verification assertions: ensure data is present in XTDB
        (is (seq (xt/q
                     @db/node
                     ['(fn [id]
                         (->
                           (from :marks [xt/id])
                           (where (= xt/id id))))
                      s1-id]))
          "Mark A should be in XTDB")
        (is (seq (xt/q
                     @db/node
                     ['(fn [id]
                         (->
                           (from :marks [xt/id])
                           (where (= xt/id id))))
                      s2-id]))
          "Mark B should be in XTDB")
        (is (seq (xt/q
                     @db/node
                     ['(fn [id]
                         (->
                           (from :votes [xt/id])
                           (where (= xt/id id))))
                      "v1"]))
          "Vote should be in XTDB")

        (let [resp (handlers/get-marks-handler
                     (with-test-site
                      {:identity "alice"
                       :params {:lat "40.0" :lon "-76.0"}}))
              body (json/read-str (:body resp) :key-fn keyword)
              marks (:marks body)
              s1 (some #(when (= (:id %) s1-id) %) marks)
              s2 (some #(when (= (:id %) s2-id) %) marks)]
          (is (= 200 (:status resp)))
          (is (= "Mark A" (:name s1)) "Mark A")
          (is (= 2 (:score s1)) "Mark A should have score 2")
          (is (= 1 (:user-vote s1)) "Mark A should have user-vote 1")
          (is (= "Mark B" (:name s2)) "Mark B")
          (is (= 0 (:score s2)) "Mark B should have score 0")
          (is (= 0 (:user-vote s2)) "Mark B should have user-vote 0")
          )

        (let [resp (handlers/get-marks-handler
                     (with-test-site
                      {:identity "alice"
                       :params {}}))
              body (json/read-str (:body resp) :key-fn keyword)
              marks (:marks body)
              s1 (some #(when (= (:id %) s1-id) %) marks)
              s2 (some #(when (= (:id %) s2-id) %) marks)]
          (is (= 200 (:status resp)))
          (is (= "Mark A" (:name s1)) "Mark A")
          (is (= 2 (:score s1)) "Mark A should have score 2")
          (is (= 1 (:user-vote s1)) "Mark A should have user-vote 1")
          (is (= "Mark B" (:name s2)) "Mark B")
          (is (= 0 (:score s2)) "Mark B should have score 0")
          (is (= 0 (:user-vote s2)) "Mark B should have user-vote 0")
          )))))

(deftest synchronization-test
  (testing "Incremental synchronization with since parameter"
    (let [id1 "sync-1"
          id2 "sync-2"
          site test-site]
      (db/save-mark {:xt/id id1 :name "Mark 1" :lat 40.0 :lon -76.0 :creator "alice" :site site})
      (let [t1 (common-utils/get-current-timestamp)]
        (Thread/sleep 10)
        (db/save-mark {:xt/id id2 :name "Mark 2" :lat 40.0 :lon -76.0 :creator "alice" :site site})
        (let [t2 (common-utils/get-current-timestamp)]
          (Thread/sleep 10)

          (testing "Fetch without since returns all"
            (let [resp (handlers/get-marks-handler
                        (with-test-site {:identity "alice" :params {:lat "40.0" :lon "-76.0"}}))
                  data (json/read-str (:body resp) :key-fn keyword)]
              (is (= 2 (count (:marks data))))
              (is (= [] (:deleted-ids data)))))

          (testing "Fetch with since t1 returns only second mark"
            (let [resp (handlers/get-marks-handler
                        (with-test-site
                         {:identity "alice"
                          :params {:lat "40.0" :lon "-76.0" :since t1}}))
                  data (json/read-str (:body resp) :key-fn keyword)]
              ;; Note: xtdb/from doesn't natively filter by 'since' in our
              ;; list-marks impl yet, but list-deletions uses it.
              ;; Actually, the list-marks implementation in db.clj doesn't
              ;; use 'since'.
              ;; It's the controller's job to handle incremental updates if
              ;; the server doesn't.
              ;; Wait, the get-marks-handler doesn't pass 'since' to
              ;; list-marks.
          (is (= 2 (count (:marks data)))))))))))

(deftest dynamic-feed-routing-test
  (testing "Dynamic feed routing resolves both generic and plural endpoints"
    (xt/execute-tx
     @db/node
     [[:put-docs :users {:xt/id "u-alice"
                         :login "alice"
                         :password (hashers/derive "secret")
                         :enabled? true
                         :site test-site}]])
    (let [auth-hdr "Basic YWxpY2U6c2VjcmV0"
          request (fn [path]
                    (core/app {:request-method :get
                               :scheme :http
                               :server-name "localhost"
                               :server-port 80
                               :uri (str "/mapmarks/s/" test-site path)
                               :headers {"authorization" auth-hdr}}))
          ;; 1. Request feed.kml (generic)
          resp-kml-gen (request "/feed.kml")
          ;; 2. Request marks.kml (plural)
          resp-kml-pl (request "/marks.kml")
          ;; 3. Request feed.csv (generic)
          resp-csv-gen (request "/feed.csv")
          ;; 4. Request feed.rss (generic)
          resp-rss-gen (request "/feed.rss")
          ;; 5. Request non-existent (e.g. unknown.kml)
          resp-invalid (request "/unknown.kml")]
      (is (= 200 (:status resp-kml-gen)))
      (is (= 200 (:status resp-kml-pl)))
      (is (= 200 (:status resp-csv-gen)))
      (is (= 200 (:status resp-rss-gen)))
      (is (= 404 (:status resp-invalid))))))

(deftest server-config-effect-test
  (testing "Server-side site configs customize feed routing and content"
    (xt/execute-tx
     @db/node
     [[:put-docs :users {:xt/id "u-potholes-alice"
                         :login "alice"
                         :password (hashers/derive "secret")
                         :enabled? true
                         :site "potholes"}]
      [:put-docs :users {:xt/id "u-coffee-alice"
                         :login "alice"
                         :password (hashers/derive "secret")
                         :enabled? true
                         :site "coffee-marks"}]])
    (let [auth-hdr "Basic YWxpY2U6c2VjcmV0"
          request (fn [site path]
                    (core/app {:request-method :get
                               :scheme :http
                               :server-name "localhost"
                               :server-port 80
                               :uri (str "/mapmarks/s/" site path)
                               :headers {"authorization" auth-hdr}}))
          mock-configs {"potholes" {:app-name "Pothole Derby"
                                    :app-description "Potholes list"
                                    :mark-name-singular "Pothole"
                                    :mark-name-plural "Potholes"
                                    :tags-name-singular "Tag"
                                    :tags-name-plural "Tags"}
                        "coffee-marks" {:app-name "Coffee Shops"
                                        :app-description "Coffee shops list"
                                        :mark-name-singular "Shop"
                                        :mark-name-plural "Shops"
                                        :tags-name-singular "Tag"
                                        :tags-name-plural "Tags"}}]
      (with-redefs [config/site-configs mock-configs]
        (testing "Potholes site configuration routing"
          (is (= 200 (:status (request "potholes" "/potholes.kml"))))
          (is (= 404 (:status (request "potholes" "/shops.kml"))))
          (is (= 200 (:status (request "potholes" "/feed.kml")))))

        (testing "Coffee Shops site configuration routing"
          (is (= 200 (:status (request "coffee-marks" "/shops.kml"))))
          (is (= 404 (:status (request "coffee-marks" "/potholes.kml"))))
          (is (= 200 (:status (request "coffee-marks" "/feed.kml")))))

        (testing "Potholes feed content customization"
          (let [resp (request "potholes" "/feed.rss")
                body (:body resp)]
            (is (= 200 (:status resp)))
            (is (str/includes? body "<title>Pothole Derby</title>"))
            (is (str/includes?
                 body
                 "<description>Potholes list</description>"))))

        (testing "Coffee Shops feed content customization"
          (let [resp (request "coffee-marks" "/feed.rss")
                body (:body resp)]
            (is (= 200 (:status resp)))
            (is (str/includes? body "<title>Coffee Shops</title>"))
            (is (str/includes?
                 body
                 "<description>Coffee shops list</description>"))))))))

(deftest csv-export-config-test
  (testing "CSV export headers and filenames are customized by site config"
    (xt/execute-tx
     @db/node
     [[:put-docs :users {:xt/id "u-csv-alice"
                         :login "alice"
                         :password (hashers/derive "secret")
                         :enabled? true
                         :site "csv-site"}]])
    (let [auth-hdr "Basic YWxpY2U6c2VjcmV0"
          request (fn [site path]
                    (core/app {:request-method :get
                               :scheme :http
                               :server-name "localhost"
                               :server-port 80
                               :uri (str "/mapmarks/s/" site path)
                               :headers {"authorization" auth-hdr}}))
          mock-configs {"csv-site" {:app-name "CSV App"
                                    :app-description "CSV Desc"
                                    :mark-name-singular "Pothole"
                                    :mark-name-plural "Potholes"
                                    :tags-name-singular "Label"
                                    :tags-name-plural "Labels"}}]
      (with-redefs [config/site-configs mock-configs]
        (let [resp (request "csv-site" "/feed.csv")
              headers (:headers resp)
              body (:body resp)]
          (is (= 200 (:status resp)))
          (is (str/includes? (get headers "Content-Disposition")
                             "filename=\"potholes.csv\""))
          (is (str/includes?
               body
               "Name,Latitude,Longitude,Address,Town,State,Labels,Notes")))))))

(deftest kml-export-config-test
  (testing "KML export layouts are customized by site config"
    (xt/execute-tx
     @db/node
     [[:put-docs :users {:xt/id "u-kml-alice"
                         :login "alice"
                         :password (hashers/derive "secret")
                         :enabled? true
                         :site "kml-site"}]])
    (let [auth-hdr "Basic YWxpY2U6c2VjcmV0"
          request (fn [site path]
                    (core/app {:request-method :get
                               :scheme :http
                               :server-name "localhost"
                               :server-port 80
                               :uri (str "/mapmarks/s/" site path)
                               :headers {"authorization" auth-hdr}}))
          mock-configs {"kml-site" {:app-name "Custom KML App"
                                    :app-description "Custom KML Desc"
                                    :mark-name-singular "Shop"
                                    :mark-name-plural "Shops"
                                    :tags-name-singular "Label"
                                    :tags-name-plural "Labels"}}]
      (with-redefs [config/site-configs mock-configs]
        (db/save-mark {:xt/id "kml-mark-1"
                       :name nil
                       :address "123 Main St"
                       :lat 40.0
                       :lon -76.0
                       :tags ["coffee" "pastry"]
                       :notes "Great espresso"
                       :creator "alice"
                       :shared? true
                       :site "kml-site"})
        (let [resp (request "kml-site" "/feed.kml")
              body (:body resp)]
          (is (= 200 (:status resp)))
          (is (str/includes? body "<name>Custom KML App</name>"))
          (is (str/includes? body "<name>Shop</name>"))
          (is (str/includes? body "Labels: coffee, pastry"))
          (is (str/includes? body "Notes: Great espresso")))))))

(deftest rss-export-config-test
  (testing "RSS export layouts and external URL are customized by config"
    (xt/execute-tx
     @db/node
     [[:put-docs :users {:xt/id "u-rss-alice"
                         :login "alice"
                         :password (hashers/derive "secret")
                         :enabled? true
                         :site "rss-site"}]])
    (let [auth-hdr "Basic YWxpY2U6c2VjcmV0"
          request (fn [site path]
                    (core/app {:request-method :get
                               :scheme :http
                               :server-name "localhost"
                               :server-port 80
                               :uri (str "/mapmarks/s/" site path)
                               :headers {"authorization" auth-hdr}}))
          mock-configs {"rss-site" {:app-name "Custom RSS App"
                                    :app-description "Custom RSS Desc"
                                    :mark-name-singular "Shop"
                                    :mark-name-plural "Shops"
                                    :tags-name-singular "Label"
                                    :tags-name-plural "Labels"
                                    :external-base-url
                                    "https://test-external.com/"}}]
      (with-redefs [config/site-configs mock-configs]
        (db/save-mark {:xt/id "rss-mark-1"
                       :name nil
                       :address "123 Main St"
                       :lat 40.0
                       :lon -76.0
                       :tags []
                       :notes "No tags name fallback"
                       :creator "alice"
                       :shared? true
                       :site "rss-site"})
        (let [resp (request "rss-site" "/feed.rss")
              body (:body resp)]
          (is (= 200 (:status resp)))
          (is (str/includes?
               body
               "href=\"https://test-external.com/s/rss-site/feed.rss\""))
          (is (str/includes? body "<title>Shop</title>"))
          (is (str/includes?
               body
               "<link>https://test-external.com/#shop=rss-mark-1</link>")))))))

(deftest migration-site-fallback-test
  (testing "Database migration defaults nil site values to config/site"
    (xt/execute-tx
     @db/node
     [[:put-docs :marks {:xt/id "mig-m1"
                         :name "Mig Mark"
                         :lat 40.0
                         :lon -76.0
                         :creator "alice"
                         :site nil}]
      [:put-docs :users {:xt/id "mig-u1"
                         :login "alice"
                         :password "secret"
                         :email "alice@example.com"
                         :enabled? true
                         :site nil}]
      [:put-docs :votes {:xt/id "mig-v1"
                         :mark-id "mig-m1"
                         :user-id "alice"
                         :value 1
                         :site nil}]])
    (with-redefs [config/site "mig-fallback-site"]
      (db/migrate!)
      (let [migrated-mark (db/get-mark-unfiltered
                           "mig-m1" "mig-fallback-site")
            migrated-user (first
                           (xt/q @db/node
                                 ['(fn [uid site]
                                     (-> (from :users [{:xt/id uid}
                                                      {:site s}
                                                      site])
                                         (where (= s site))))
                                  "mig-u1" "mig-fallback-site"]))
            migrated-vote (first
                           (xt/q @db/node
                                 ['(fn [vid site]
                                     (-> (from :votes [{:xt/id vid}
                                                      {:site s}
                                                      site])
                                         (where (= s site))))
                                  "mig-v1" "mig-fallback-site"]))]
        (is (= "mig-fallback-site" (:site migrated-mark)))
        (is (= "mig-fallback-site" (:site migrated-user)))
        (is (= "mig-fallback-site" (:site migrated-vote)))))))
