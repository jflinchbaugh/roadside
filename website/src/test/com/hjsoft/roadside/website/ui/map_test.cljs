(ns com.hjsoft.roadside.website.ui.map-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [helix.core :refer [$]]
            ["@testing-library/react" :as tlr]
            [com.hjsoft.roadside.website.ui.map :as sut]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.leaflet-init]
            [goog.object :as gobj]
            ["react" :as react]))

(use-fixtures :each
  {:after tlr/cleanup})

(defn render-with-context [component context-val]
  (let [app-ctx state/app-context]
    (tlr/render
     (react/createElement (gobj/get app-ctx "Provider")
                          #js {:value context-val}
                          component))))

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

(deftest leaflet-map-render-test
  (testing "leaflet-map renders container div"
    (let [mock-l (create-mock-leaflet)
          _ (sut/set-leaflet! mock-l)
          context-val {:state {:stands [] :map-center [0 0] :selected-stand nil}
                       :dispatch (fn [_])
                       :user-location {:location nil :is-locating false}}
          res (render-with-context
               ($ sut/leaflet-map {:div-id "test-map" :zoom-level 10})
               context-val)
          container (.-container res)]
      (let [map-div (.querySelector container "#test-map")]
        (is (some? map-div) "Map div with id test-map should be present")))))

(deftest leaflet-map-locating-test
  (testing "leaflet-map shows locating overlay"
    (let [mock-l (create-mock-leaflet)
          _ (sut/set-leaflet! mock-l)
          context-val {:state {:stands [] :map-center [0 0] :selected-stand nil}
                       :dispatch (fn [_])
                       :user-location {:location nil :is-locating true}}
          res (render-with-context
               ($ sut/leaflet-map {:div-id "test-map" :zoom-level 10})
               context-val)
          container (.-container res)]
      (is (some? (tlr/queryByText container "Locating..."))
          "Should show Locating... text"))))
