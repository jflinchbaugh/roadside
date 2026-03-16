(ns com.hjsoft.roadside.website.ui.forms-test
  (:require [cljs.test :refer [deftest is testing use-fixtures async]]
            [helix.core :refer [$]]
            ["@testing-library/react" :as tlr]
            [com.hjsoft.roadside.website.leaflet-init]
            [com.hjsoft.roadside.website.ui.forms :as forms]
            [com.hjsoft.roadside.website.state :as state]
            [com.hjsoft.roadside.website.api :as api]
            [cljs.core.async :refer [go <! put! chan]]
            [goog.object :as gobj]
            ["react" :as react]))

(use-fixtures :each
  {:after tlr/cleanup})

(defn- mock-http-response [response]
  (let [c (chan)]
    (put! c response)
    c))

(defn render-with-context [component context-val]
  (let [app-ctx state/app-context]
    (tlr/render
     (react/createElement (gobj/get app-ctx "Provider")
                          #js {:value context-val}
                          component))))

(deftest settings-dialog-registration-failure-test
  (async done
    (testing "settings-dialog displays registration errors"
      (let [dispatch (fn [_])
            context-val {:state {:settings {}}
                         :dispatch dispatch
                         :ui {:set-show-settings-dialog (fn [_])}}
            mock-register (fn [_ _ _] 
                            (mock-http-response {:success false :error ["Username taken"]}))
            res (render-with-context ($ forms/settings-dialog {:register-fn mock-register}) context-val)
            container (.-container res)]
        
        ;; Switch to registration
        (let [register-link (tlr/getByText container "Don't have an account? Register")]
          (tlr/fireEvent.click register-link))

        ;; Fill out fields
        (let [user-input (tlr/getByLabelText container "User:")
              pass-input (tlr/getByLabelText container "Password:")]
          (tlr/fireEvent.change user-input #js {:target #js {:value "newuser"}})
          (tlr/fireEvent.change pass-input #js {:target #js {:value "password"}}))

        (let [register-btn (tlr/getByText container "Register")]
          (tlr/fireEvent.click register-btn)
          
          ;; Wait for error to appear
          (.then (tlr/waitFor (fn [] 
                                (if (tlr/queryByText container "Username taken")
                                  true
                                  (throw (js/Error. "Still waiting")))))
                 (fn []
                   (is (some? (tlr/queryByText container "Username taken")) 
                       "Error message should be visible")
                   (done))))))))

(deftest stand-form-cancel-test
  (testing "stand-form can be cancelled"
    (let [cancelled (atom false)
          context-val {:state {:settings {} :map-center [0 0]}
                       :dispatch (fn [_])
                       :ui {:editing-stand nil
                            :set-show-form (fn [v] (when (false? v) (reset! cancelled true)))
                            :set-editing-stand (fn [_])}}
          res (render-with-context ($ forms/stand-form) context-val)
          container (.-container res)
          cancel-btn (tlr/getByTitle container "Cancel")]
      (tlr/fireEvent.click cancel-btn)
      (is (true? @cancelled) "Form should be cancelled"))))
