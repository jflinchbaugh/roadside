(ns com.hjsoft.roadside.website.core-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [helix.core :refer [$]]
            ["@testing-library/react" :as tlr]
            [com.hjsoft.roadside.website.leaflet-init]
            [com.hjsoft.roadside.website.core :as sut]
            [com.hjsoft.roadside.website.controller :as controller]
            [com.hjsoft.roadside.website.ui.map :as ui-map]
            ["react" :as react]))

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
      (with-redefs [controller/fetch-remote-stands! (fn
                                                      ([_ _] nil)
                                                      ([_ _ _] nil))
                    controller/save-local-data! (fn [_ _ _ _] nil)]
        (let [mock-geo #js {:getCurrentPosition (fn [success _ _])}
              res (tlr/render ($ sut/app {:geolocation mock-geo}))
              container (.-container res)]
          (is (some? (tlr/queryByText container "Roadside Stands"))
            "Header title should be present")
          (is (some? (tlr/queryByText container "Add Stand"))
            "Add Stand button should be present"))))))
