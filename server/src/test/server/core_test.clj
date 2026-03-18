(ns server.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [server.core :as core]
            [server.db :as db]
            [server.auth :as auth]
            [server.handlers :as handlers]
            [buddy.hashers :as hashers]
            [xtdb.api :as xt]
            [server.xtdb-container :as xtn]
            [org.httpkit.client :as hkc]
            [clojure.string :as str]
            [clojure.data.json :as json])
  (:import [java.io ByteArrayInputStream]))

(defn with-xtdb-container [f]
  (xtn/with-xtdb-client
    (fn [n]
      (reset! db/node n)
      (f)
      (reset! db/node nil))))

(use-fixtures :each with-xtdb-container)

(deftest ping-test
  (testing "Ping handler returns 200 pong"
    (let [response (handlers/ping-handler {})]
      (is (= 200 (:status response)))
      (is (= "\"pong\"" (:body response))))))

(deftest register-test
  (testing "Register handler saves user to DB"
    (let [req {:params {:login "alice"
                        :password "secret-password"
                        :email "alice@example.com"}}
          response (handlers/register-handler req)]
      (is (= 201 (:status response)))
      (let [user (db/get-user "alice")]
        (is (= "alice" (:login user)))
        (is (:valid (hashers/verify "secret-password" (:password user))))
        (is (= "alice@example.com" (:email user))))))
  (testing "Register handler requires email"
    (let [req {:params {:login "bob" :password "secret-pass"}}
          response (handlers/register-handler req)]
      (is (= 400 (:status response)))
      (is (some #{"should match regex"} (get-in (json/read-str (:body response) :key-fn keyword) [:errors :email])))))
  (testing "Register handler requires login"
    (let [req {:params {:email "bob@example.com" :password "secret-pass"}}
          response (handlers/register-handler req)]
      (is (= 400 (:status response)))
      (is (some #{"should match regex"} (get-in (json/read-str (:body response) :key-fn keyword) [:errors :login])))))
  (testing "Register handler requires password"
    (let [req {:params {:login "bob" :email "bob@example.com"}}
          response (handlers/register-handler req)]
      (is (= 400 (:status response)))
      (is (some #{"should be a string"} (get-in (json/read-str (:body response) :key-fn keyword) [:errors :password])))))
  (testing "Register handler requires all fields"
    (let [req {:params {}}
          response (handlers/register-handler req)]
      (is (= 400 (:status response)))
      (let [errors (:errors (json/read-str (:body response) :key-fn keyword))]
        (is (some #{"should match regex"} (:email errors)))
        (is (some #{"should match regex"} (:login errors)))
        (is (some #{"should be a string"} (:password errors))))))
  (testing "Register handler with invalid inputs"
    (let [req {:params {:login "a" :password "short" :email "not-an-email"}}
          response (handlers/register-handler req)]
      (is (= 400 (:status response)))
      (let [errors (:errors (json/read-str (:body response) :key-fn keyword))]
        (is (some #{"should match regex"} (:email errors)))
        (is (some #{"should match regex"} (:login errors)))
        (is (some #{"should be at least 8 characters"} (:password errors))))) )
  (testing "Register handler with a duplicate"
    (let [req {:params {:login "alice"
                        :password "again-secret"
                        :email "alice2@example.com"}}
          response (handlers/register-handler req)]
      (is (= 403 (:status response)))
      (is (= ["login not available"] (:errors (json/read-str (:body response) :key-fn keyword))))
      (let [user (db/get-user "alice")]
        (is (= "alice" (:login user)))
        (is (:valid (hashers/verify "secret-password" (:password user))) "password not touched")))))

(deftest stands-test
  (testing "Stands handlers"
    (let [stand-doc {:name "Morning Coffee" :address "Main St" :coordinate "40.0379, -76.3055"}
          body (json/write-str stand-doc)
          create-req {:body (ByteArrayInputStream. (.getBytes body))
                      :identity "alice"}
          create-resp (handlers/create-stand-handler create-req)]
      (is (= 201 (:status create-resp)))
      (let [created-stand (json/read-str (:body create-resp) :key-fn keyword)
            id (:id created-stand)]
        (is (not (nil? id)))
        (is (= "Morning Coffee" (:name created-stand)))

        (testing "Get all stands (no filter)"
          (let [get-resp (handlers/get-stands-handler {:identity "alice"})
                stands (json/read-str (:body get-resp) :key-fn keyword)]
            (is (= 200 (:status get-resp)))
            (is (>= (count stands) 1))))

        (testing "Get stands within radius"
          ;; Lancaster, PA: 40.0379, -76.3055
          (let [get-resp (handlers/get-stands-handler {:params {:lat "40.0" :lon "-76.0"} :identity "alice"})
                stands (json/read-str (:body get-resp) :key-fn keyword)]
            (is (= 200 (:status get-resp)))
            (is (>= (count stands) 1) "Should find the stand near Lancaster")))

        (testing "Get stands outside radius"
          ;; Los Angeles: 34.0522, -118.2437 (far from Lancaster, PA)
          (let [get-resp (handlers/get-stands-handler {:params {:lat "34.0" :lon "-118.0"} :identity "alice"})
                stands (json/read-str (:body get-resp) :key-fn keyword)]
            (is (= 200 (:status get-resp)))
            (is (= 0 (count (filter #(= (:id %) id) stands))) "Should NOT find the Lancaster stand from LA")))

        (testing "Get single stand"
          (let [get-resp (handlers/get-stand-handler {:path-params {:id id} :identity "alice"})]
            (is (= 200 (:status get-resp)))
            (is (= "Morning Coffee" (:name (json/read-str (:body get-resp) :key-fn keyword))))))

        (testing "Update stand"
          (let [update-doc (assoc created-stand :name "Evening Coffee")
                update-body (json/write-str update-doc)
                update-req {:path-params {:id id}
                            :body (ByteArrayInputStream. (.getBytes update-body))
                            :identity "alice"}
                update-resp (handlers/update-stand-handler update-req)]
            (is (= 200 (:status update-resp)))
            (is (= "Evening Coffee" (:name (json/read-str (:body update-resp) :key-fn keyword))))))

        (testing "Update non-existent stand (upsert behavior)"
          (let [non-existent-id "missing-id"
                update-doc {:name "New Stand" :address "Unknown"}
                update-body (json/write-str update-doc)
                update-req {:path-params {:id non-existent-id}
                            :body (ByteArrayInputStream. (.getBytes update-body))
                            :identity "alice"}
                update-resp (handlers/update-stand-handler update-req)]
            (is (= 200 (:status update-resp)))
            (let [created (json/read-str (:body update-resp) :key-fn keyword)]
              (is (= "New Stand" (:name created)))
              (is (= non-existent-id (:id created)))
              ;; Verify it's actually in the DB
              (let [get-resp (handlers/get-stand-handler {:path-params {:id non-existent-id} :identity "alice"})]
                (is (= 200 (:status get-resp)))
                (is (= "New Stand" (:name (json/read-str (:body get-resp) :key-fn keyword))))))))

        (testing "Delete stand"
          (let [del-resp (handlers/delete-stand-handler {:path-params {:id id} :identity "alice"})]
            (is (= 200 (:status del-resp)))
            (is (nil? (db/get-stand id)))))

        (testing "Delete non-existent stand"
          (let [del-resp (handlers/delete-stand-handler {:path-params {:id "non-existent"} :identity "alice"})]
            (is (= 200 (:status del-resp)))
            (is (= "'non-existent' deleted" (:message (json/read-str (:body del-resp) :key-fn keyword))))))))))

(deftest stands-visibility-test
  (testing "Stands visibility filtering"
    (let [alice-stand {:xt/id "alice-1" :name "Alice Stand" :shared? false}
          bob-private {:xt/id "bob-private" :name "Bob Private" :shared? false}
          bob-shared {:xt/id "bob-shared" :name "Bob Shared" :shared? true}]

      ;; Setup stands in DB
      (xt/submit-tx @db/node [[:put-docs :stands (assoc alice-stand :creator "alice")]
                              [:put-docs :stands (assoc bob-private :creator "bob")]
                              [:put-docs :stands (assoc bob-shared :creator "bob")]])

      (testing "Alice sees her own and bob's shared stands"
        (let [req {:identity "alice"}
              resp (handlers/get-stands-handler req)
              stands (json/read-str (:body resp) :key-fn keyword)
              ids (set (map :id stands))]
          (is (= 200 (:status resp)))
          (is (contains? ids "alice-1"))
          (is (contains? ids "bob-shared"))
          (is (not (contains? ids "bob-private")))))

      (testing "Bob sees his own and bob's shared stands (all 3 in this case since he owns both private and shared)"
        (let [req {:identity "bob"}
              resp (handlers/get-stands-handler req)
              stands (json/read-str (:body resp) :key-fn keyword)
              ids (set (map :id stands))]
          (is (= 200 (:status resp)))
          (is (contains? ids "bob-private"))
          (is (contains? ids "bob-shared"))
          ;; Bob should NOT see Alice's private stand
          (is (not (contains? ids "alice-1")))))

      (testing "Unauthenticated user (if allowed, though routes usually require auth) sees only shared stands"
        ;; Although the route has auth middleware, the handler itself should handle nil identity gracefully if it ever reached there
        (let [req {:identity nil}
              resp (handlers/get-stands-handler req)
              stands (json/read-str (:body resp) :key-fn keyword)
              ids (set (map :id stands))]
          (is (= 200 (:status resp)))
          (is (= #{"bob-shared"} ids))))

      (testing "Individual stand visibility"
        (is (= 200 (:status (handlers/get-stand-handler {:path-params {:id "alice-1"} :identity "alice"}))))
        (is (= 404 (:status (handlers/get-stand-handler {:path-params {:id "bob-private"} :identity "alice"}))))
        (is (= 200 (:status (handlers/get-stand-handler {:path-params {:id "bob-shared"} :identity "alice"}))))
        (is (= 200 (:status (handlers/get-stand-handler {:path-params {:id "bob-private"} :identity "bob"}))))))))

(deftest creator-test
  (testing "Creator value behavior"
    (let [stand-id "stand-1"
          stand-doc {:id stand-id :name "Creator Test Stand" :creator "malicious-user"}
          body (json/write-str stand-doc)
          create-req {:body (ByteArrayInputStream. (.getBytes body))
                      :identity "alice"}
          create-resp (handlers/create-stand-handler create-req)]
      (is (= 201 (:status create-resp)))
      (let [created-stand (json/read-str (:body create-resp) :key-fn keyword)]
        (is (= "alice" (:creator created-stand)) "Creator should be set from identity, ignoring client input")
        (is (= stand-id (:id created-stand)))

        (testing "Updating stand preserves creator"
          (let [update-doc (assoc created-stand :name "Updated Name" :creator "malicious-user")
                update-body (json/write-str update-doc)
                update-req {:path-params {:id stand-id}
                            :body (ByteArrayInputStream. (.getBytes update-body))
                            :identity "alice"}
                update-resp (handlers/update-stand-handler update-req)]
            (is (= 200 (:status update-resp)))
            (let [updated-stand (json/read-str (:body update-resp) :key-fn keyword)]
              (is (= "alice" (:creator updated-stand)) "Creator should be preserved from existing record, ignoring client input and current identity"))))

        (testing "Updating stand by non-owner is forbidden"
          (let [update-doc (assoc created-stand :name "Malicious Update")
                update-body (json/write-str update-doc)
                update-req {:path-params {:id stand-id}
                            :body (ByteArrayInputStream. (.getBytes update-body))
                            :identity "bob"}
                update-resp (handlers/update-stand-handler update-req)]
            (is (= 403 (:status update-resp)))
            (is (= "Forbidden: You do not own this stand" (:error (json/read-str (:body update-resp) :key-fn keyword))))))

        (testing "Deleting stand by non-owner is forbidden"
          (let [del-req {:path-params {:id stand-id}
                         :identity "bob"}
                del-resp (handlers/delete-stand-handler del-req)]
            (is (= 403 (:status del-resp)))
            (is (= "Forbidden: You do not own this stand" (:error (json/read-str (:body del-resp) :key-fn keyword))))))

        (testing "Upserting new stand sets creator from current identity"
          (let [upsert-id "stand-2"
                upsert-doc {:name "Upsert Stand" :creator "malicious-user"}
                upsert-body (json/write-str upsert-doc)
                upsert-req {:path-params {:id upsert-id}
                            :body (ByteArrayInputStream. (.getBytes upsert-body))
                            :identity "charlie"}
                upsert-resp (handlers/update-stand-handler upsert-req)]
            (is (= 200 (:status upsert-resp)))
            (let [upserted-stand (json/read-str (:body upsert-resp) :key-fn keyword)]
              (is (= "charlie" (:creator upserted-stand)) "Creator should be set from identity for new record in update handler"))))))))

(deftest upsert-test
  (testing "Updating a non-existent stand creates it (upsert)"
    (let [id "upsert-id"
          stand-doc {:name "Upserted Stand" :address "Upsert Lane"}
          body (json/write-str stand-doc)
          req {:path-params {:id id}
               :body (ByteArrayInputStream. (.getBytes body))
               :identity "alice"}
          resp (handlers/update-stand-handler req)]
      (is (= 200 (:status resp)))
      (let [created (json/read-str (:body resp) :key-fn keyword)]
        (is (= id (:id created)))
        (is (= "Upserted Stand" (:name created)))
        (is (= "alice" (:creator created)))

        ;; Verify it persists in DB
        (let [get-resp (handlers/get-stand-handler {:path-params {:id id} :identity "alice"})]
          (is (= 200 (:status get-resp)))
          (is (= "Upserted Stand" (:name (json/read-str (:body get-resp) :key-fn keyword)))))))))

(deftest auth-test
  (testing "authfn"
    (reset! db/node @db/node) ;; ensure atom is initialized if needed
    (xt/submit-tx @db/node [[:put-docs :users {:xt/id "u1" :login "bob" :password (hashers/derive "pass")}]])
    (let [user (db/get-user "bob")]
      (is (= "bob" (:login user)))
      (is (:valid (hashers/verify "pass" (:password user)))))))

(deftest geocode-proxy-test
  (testing "Geocode proxy handler"
    (testing "Successful geocoding"
      (let [mock-response {:status 200
                           :body (json/write-str [{:lat "40.0379" :lon "-76.3055"}])}]
        (with-redefs [hkc/get (fn [_ _] (atom mock-response))]
          (let [req {:params {:q "Lancaster, PA"}
                     :identity "alice"}
                resp (handlers/geocode-handler req)]
            (is (= 200 (:status resp)))
            (is (= [{:lat "40.0379" :lon "-76.3055"}]
                   (json/read-str (:body resp) :key-fn keyword)))))))

    (testing "Address not found"
      (let [mock-response {:status 200 :body "[]"}]
        (with-redefs [hkc/get (fn [_ _] (atom mock-response))]
          (let [req {:params {:q "Middle of Nowhere"}
                     :identity "alice"}
                resp (handlers/geocode-handler req)]
            (is (= 200 (:status resp)))
            (is (= [] (json/read-str (:body resp))))))))

    (testing "Missing address parameter"
      (let [resp (handlers/geocode-handler {:params {} :identity "alice"})]
        (is (= 400 (:status resp)))
        (is (= "Missing address" (:error (json/read-str (:body resp) :key-fn keyword))))))

    (testing "Nominatim error (500)"
      (let [mock-response {:status 500 :body "Internal Server Error"}]
        (with-redefs [hkc/get (fn [_ _] (atom mock-response))]
          (let [req {:params {:q "Lancaster, PA"}
                     :identity "alice"}
                resp (handlers/geocode-handler req)]
            (is (= 502 (:status resp)))
            (is (str/includes? (:body resp) "Nominatim error")))))))

  (testing "Reverse geocode proxy handler"
    (testing "Successful reverse geocoding"
      (let [mock-response {:status 200
                           :body (json/write-str {:address {:road "Main St" :city "Lancaster" :state "PA"}})}]
        (with-redefs [hkc/get (fn [_ _] (atom mock-response))]
          (let [req {:params {:lat "40.0379" :lon "-76.3055"}
                     :identity "alice"}
                resp (handlers/reverse-geocode-handler req)]
            (is (= 200 (:status resp)))
            (is (= {:road "Main St" :city "Lancaster" :state "PA"}
                   (:address (json/read-str (:body resp) :key-fn keyword))))))))

    (testing "Missing parameters"
      (let [resp (handlers/reverse-geocode-handler {:params {:lat "40.0"} :identity "alice"})]
        (is (= 400 (:status resp)))
        (is (= "Missing lat or lon" (:error (json/read-str (:body resp) :key-fn keyword))))))

    (testing "Nominatim error"
      (let [mock-response {:status 404 :body "Not Found"}]
        (with-redefs [hkc/get (fn [_ _] (atom mock-response))]
          (let [req {:params {:lat "0" :lon "0"}
                     :identity "alice"}
                resp (handlers/reverse-geocode-handler req)]
            (is (= 502 (:status resp)))
            (is (str/includes? (:body resp) "Nominatim error"))))))))
