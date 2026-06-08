(ns com.hjsoft.mapmarks.website.core-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
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
