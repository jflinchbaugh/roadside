(ns com.hjsoft.mapmarks.website.core-test
  (:require [cljs.test :as t :refer [deftest is testing use-fixtures]]
            [helix.core :refer [$]]
            ["@testing-library/react" :as tlr]
            [com.hjsoft.mapmarks.website.leaflet-init]
            [com.hjsoft.mapmarks.website.core :as sut]
            [com.hjsoft.mapmarks.website.config :as config]
            [com.hjsoft.mapmarks.website.state :as state]
            [com.hjsoft.mapmarks.website.controller :as controller]
            [com.hjsoft.mapmarks.website.ui.map :as ui-map]))

(use-fixtures :each
  {:after tlr/cleanup})

(defn create-mock-leaflet []
  #js {:map (fn [_]
              #js {:setView (fn [& _] (this-as this this))
                   :addTo (fn [& _] (this-as this this))
                   :on (fn [& _] (this-as this this))
                   :getCenter (fn [] #js {:lat 0 :lng 0})
                   :getZoom (fn [] 10)
                   :invalidateSize (fn [] (this-as this this))
                   :removeLayer (fn [& _] (this-as this this))})
       :tileLayer (fn [_] #js {:addTo (fn [& _] (this-as this this))})
       :marker (fn [_] #js {:bindPopup (fn [& _] (this-as this this))
                            :on (fn [& _] (this-as this this))
                            :addTo (fn [& _] (this-as this this))
                            :openPopup (fn [] (this-as this this))})
       :point (fn [x y] #js {:x x :y y})
       :circleMarker (fn [_ _] #js {:addTo (fn [& _] (this-as this this))})
       :layerGroup (fn [_] #js {:addTo (fn [& _] (this-as this this))})})

(deftest app-render-test
  (testing "app component renders basic layout"
    (let [mock-l (create-mock-leaflet)
          _ (ui-map/set-leaflet! mock-l)]
      (with-redefs [controller/fetch-remote-marks! (fn
                                                      ([_ _] nil)
                                                      ([_ _ _] nil))
                    controller/save-local-data! (fn [_ _ _ _] nil)]
        (let [mock-geo #js {:getCurrentPosition (fn [success _ _])}
              res (tlr/render ($ sut/app {:geolocation mock-geo}))
              container (.-container res)]
          (is (some? (tlr/queryByText container (:app-name config/config)))
            "Header title should be present")
          (is (some? (tlr/queryByText
                      container
                      (str "Add "
                           (:mark-name-article config/config)
                           " "
                           (:mark-name-singular config/config))))
            "Add Mark button should be present"))))))

(deftest app-url-action-add-test
  (testing "app component opens add mark form when ?action=add is present"
    (let [mock-l (create-mock-leaflet)
          _ (ui-map/set-leaflet! mock-l)]
      (with-redefs [controller/fetch-remote-marks! (fn
                                                      ([_ _] nil)
                                                      ([_ _ _] nil))
                    controller/save-local-data! (fn [_ _ _ _] nil)]
        (let [mock-geo #js {:getCurrentPosition (fn [success _ _])}]
          (js/window.history.pushState #js {} "" "?action=add")
          (let [res (tlr/render ($ sut/app {:geolocation mock-geo}))
                container (.-container res)]
            (is (some? (tlr/queryByText
                        container
                        (str "Add New " (:mark-name-singular config/config))))
              "Add New Mark form should be present"))
          (js/window.history.pushState #js {} "" "/"))))))

(deftest app-select-mark-permalink-test
  (testing "selecting a mark updates the URL hash, and URL hash selects mark on load"
    (let [mock-l (create-mock-leaflet)
          _ (ui-map/set-leaflet! mock-l)]
      (with-redefs [controller/fetch-remote-marks! (fn
                                                      ([_ _] nil)
                                                      ([_ _ _] nil))
                    controller/save-local-data! (fn [_ _ _ _] nil)]
        (let [mock-geo #js {:getCurrentPosition (fn [success _ _])}]
          ;; 1. Load with hash selects the mark
          (set! (.-hash js/window.location) "#mark=xyz-123")
          (let [mock-mark {:id "xyz-123" :name "Apple Stand" :lat 40.0379 :lon -76.3055}
                state-with-marks (assoc (state/initial-app-state)
                                   :marks [mock-mark]
                                   :selected-mark mock-mark)]
            (js/console.log "MOCK STATE:" (clj->js state-with-marks))
            (with-redefs [state/initial-app-state (constantly state-with-marks)]
              (let [res (tlr/render ($ sut/app {:geolocation mock-geo}))
                    container (.-container res)]
                (js/console.log "RENDERED HTML:" (.-innerHTML container))
                (is (some? (.querySelector container ".selected-mark"))
                    "Mark should be selected when loaded with permalink hash"))))
          ;; 2. Selecting a mark updates URL hash
          (set! (.-hash js/window.location) "")
          (let [state-with-marks (assoc (state/initial-app-state)
                                   :marks [{:id "xyz-123" :name "Apple Stand" :lat 40.0379 :lon -76.3055}])]
            (with-redefs [state/initial-app-state (constantly state-with-marks)]
              (let [res (tlr/render ($ sut/app {:geolocation mock-geo}))
                    container (.-container res)
                    mark-item (.querySelector container ".mark-item")]
                (is (nil? (.querySelector container ".selected-mark"))
                    "Mark should not be selected initially")
                (tlr/fireEvent.click mark-item)
                (is (= "#mark=xyz-123" js/window.location.hash)
                    "URL hash should be updated to permalink format when mark is clicked")
                (is (some? (.querySelector container ".selected-mark"))
                    "Mark should be selected in UI"))))
          (set! (.-hash js/window.location) ""))))))

(deftest app-permalink-missing-mark-test
  (testing "fetches remote mark if not in local list"
    (let [mock-l (create-mock-leaflet)
          _ (ui-map/set-leaflet! mock-l)
          fetch-called (atom nil)]
      (with-redefs [controller/fetch-remote-marks! (fn
                                                      ([_ _] nil)
                                                      ([_ _ _] nil))
                    controller/fetch-remote-mark! (fn
                                                     ([_ _ mark-id]
                                                      (reset! fetch-called
                                                              mark-id))
                                                     ([_ _ _ _] nil))
                    controller/save-local-data! (fn [_ _ _ _] nil)]
        (let [mock-geo #js {:getCurrentPosition (fn [success _ _])}]
          (set! (.-hash js/window.location) "#mark=missing-id")
          (tlr/render ($ sut/app {:geolocation mock-geo}))
          (is (= "missing-id" @fetch-called)
              "Should fetch missing mark-id from URL hash")
          (set! (.-hash js/window.location) ""))))))

(deftest app-no-gps-on-permalink-test
  (testing "does not query user location if permalink hash is present"
    (let [mock-l (create-mock-leaflet)
          _ (ui-map/set-leaflet! mock-l)
          gps-called (atom false)]
      (with-redefs [controller/fetch-remote-marks! (fn
                                                      ([_ _] nil)
                                                      ([_ _ _] nil))
                    controller/fetch-remote-mark! (fn
                                                     ([_ _ _] nil)
                                                     ([_ _ _ _] nil))
                    controller/save-local-data! (fn [_ _ _ _] nil)]
        (let [mock-geo #js {:getCurrentPosition (fn [success _ _]
                                                  (reset! gps-called true))}]
          (set! (.-hash js/window.location) "#mark=xyz-123")
          (tlr/render ($ sut/app {:geolocation mock-geo}))
          (is (false? @gps-called)
              "Should not query GPS when loaded with permalink")
          (set! (.-hash js/window.location) ""))))))

(deftest app-location-tracking-and-recenter-test
  (testing (str "tracks location, centers map initially, "
                "stops on pan, resumes on btn")
    (let [set-view-calls (atom [])
          event-handlers (atom {})
          mock-map #js {:setView (fn [center zoom opts]
                                   (swap! set-view-calls conj (js->clj center))
                                   (this-as this this))
                        :addTo (fn [& _] (this-as this this))
                        :on (fn [event-names handler]
                              (doseq [evt (.split event-names " ")]
                                (swap! event-handlers update evt
                                       (fnil conj []) handler))
                              (this-as this this))
                        :getCenter (fn [] #js {:lat 0 :lng 0})
                        :getZoom (fn [] 10)
                        :invalidateSize (fn [] (this-as this this))
                        :removeLayer (fn [& _] (this-as this this))}
          mock-l #js {:map (fn [_] mock-map)
                      :tileLayer (fn [_]
                                   #js {:addTo (fn [& _] (this-as this this))})
                      :marker (fn [_]
                                #js {:bindPopup (fn [& _] (this-as this this))
                                     :on (fn [& _] (this-as this this))
                                     :addTo (fn [& _] (this-as this this))
                                     :openPopup (fn [] (this-as this this))})
                      :point (fn [x y] #js {:x x :y y})
                      :circleMarker (fn [_ _]
                                      #js {:addTo (fn [& _]
                                                    (this-as this this))})
                      :layerGroup (fn [_]
                                    #js {:addTo (fn [& _]
                                                  (this-as this this))})}
          _ (ui-map/set-leaflet! mock-l)
          geo-cb (atom nil)
          mock-geo #js {:watchPosition (fn [success-cb _ _]
                                         (reset! geo-cb success-cb)
                                         1)}
          state-clean (assoc (state/initial-app-state) :marks [])]
      (with-redefs [controller/fetch-remote-marks! (fn
                                                      ([_ _] nil)
                                                      ([_ _ _] nil))
                    controller/save-local-data! (fn [_ _ _ _] nil)
                    state/initial-app-state (constantly state-clean)]
        (let [res (tlr/render ($ sut/app {:geolocation mock-geo}))
              container (.-container res)]
          ;; 1. Initial geolocation tick should center the map
          (tlr/act
           (fn []
             (when @geo-cb
               (@geo-cb #js {:coords #js {:latitude 40.1
                                          :longitude -76.1}}))))
          (is (some (fn [c] (and (= 40.1 (first c)) (= -76.1 (second c))))
                    @set-view-calls)
              "Map should be centered on initial user location")

          ;; 2. Subsequent location tick should move map (follow-user mode)
          (reset! set-view-calls [])
          (tlr/act
           (fn []
             (when @geo-cb
               (@geo-cb #js {:coords #js {:latitude 40.2
                                          :longitude -76.2}}))))
          (is (some (fn [c] (and (= 40.2 (first c)) (= -76.2 (second c))))
                    @set-view-calls)
              "Map should follow user location updates")

          ;; 3. User manually moves map (simulate dragstart with originalEvent)
          (reset! set-view-calls [])
          (tlr/act
           (fn []
             (doseq [h (get @event-handlers "dragstart")]
               (h #js {:type "dragstart" :originalEvent #js {}}))))

          ;; 4. Next location tick should NOT recenter the map
          (reset! set-view-calls [])
          (tlr/act
           (fn []
             (when @geo-cb
               (@geo-cb #js {:coords #js {:latitude 40.3
                                          :longitude -76.3}}))))
          (is (empty? @set-view-calls)
              "Map should not recenter after user manually moved the map")

          ;; 5. User clicks the location button
          (let [loc-btn (.querySelector container ".location-btn")]
            (is (some? loc-btn) "Location button should exist")
            (tlr/act
             (fn []
               (tlr/fireEvent.click loc-btn)))
            (is (some (fn [c] (and (= 40.3 (first c)) (= -76.3 (second c))))
                      @set-view-calls)
                (str "Map should recenter to current location on location "
                     "button click"))

            ;; 6. Subsequent location tick should follow again
            (reset! set-view-calls [])
            (tlr/act
             (fn []
               (when @geo-cb
                 (@geo-cb #js {:coords #js {:latitude 40.4
                                            :longitude -76.4}}))))
            (is (some (fn [c] (and (= 40.4 (first c)) (= -76.4 (second c))))
                      @set-view-calls)
                (str "Map should follow location updates after location "
                     "button clicked"))))))))

(deftest app-review-mode-toggle-test
  (let [mock-l (create-mock-leaflet)
        _ (ui-map/set-leaflet! mock-l)]
    (with-redefs [controller/fetch-remote-marks! (fn
                                                   ([_ _] nil)
                                                   ([_ _ _] nil))
                  controller/save-local-data! (fn [_ _ _ _] nil)
                  controller/save-last-reviewed! (fn [_] nil)]
      (let [mock-geo #js {:getCurrentPosition (fn [success _ _])}
            res (tlr/render ($ sut/app {:geolocation mock-geo}))
            container (.-container res)
            review-btn (.querySelector container ".review-marks-btn")]
        (is (some? review-btn) "Review button should exist in action bar")
        (is (nil? (.querySelector container ".review-banner"))
            "Review banner should not be visible initially")
        ;; Click Review button to activate review mode
        (tlr/act
         (fn []
           (tlr/fireEvent.click review-btn)))
        (is (some? (.querySelector container ".review-banner"))
            "Review banner should be visible when review mode is active")
        ;; Click Done Reviewing button to exit review mode
        (let [done-btn (.querySelector container ".exit-review-btn")]
          (is (some? done-btn) "Done Reviewing button should exist")
          (tlr/act
           (fn []
             (tlr/fireEvent.click done-btn)))
          (is (nil? (.querySelector container ".review-banner"))
              "Review banner should be hidden after clicking Done Reviewing"))))))
