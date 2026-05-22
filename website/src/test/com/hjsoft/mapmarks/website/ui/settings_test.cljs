(ns com.hjsoft.mapmarks.website.ui.settings-test
  (:require [cljs.test :refer [deftest is testing use-fixtures async]]
            [helix.core :refer [$]]
            ["@testing-library/react" :as tlr]
            [com.hjsoft.mapmarks.website.ui.forms.settings :as settings]
            [com.hjsoft.mapmarks.website.state :as state]
            [goog.object :as gobj]
            ["react" :as react]
            [cljs.core.async :refer [chan put!]]))

(use-fixtures :each
  {:after tlr/cleanup})

(defn render-with-context [component context-val]
  (let [app-ctx state/app-context]
    (tlr/render
     (react/createElement (gobj/get app-ctx "Provider")
                          #js {:value context-val}
                          component))))

(deftest settings-register-arity-test
  (async done
    (testing "registration calls register-fn with 4 arguments (site, user, pass, email)"
      (let [site "test-site"
            user "alice"
            pass "secret"
            email "alice@example.com"
            called-with (atom nil)
            mock-register (fn [s u p e]
                            (reset! called-with [s u p e])
                            (let [c (chan)]
                              (put! c {:success true})
                              c))
            context-val {:state {:settings {:user user :password pass :email email :local-only? false}
                                 :config {:site site}}
                         :dispatch (fn [_])
                         :ui {:set-show-settings-dialog (fn [_])}}
            res (render-with-context ($ settings/settings-dialog {:register-fn mock-register}) context-val)
            container (.-container res)
            ;; We need to be in "registering" mode to see the button
            register-link (tlr/queryByText container "Don't have an account? Register")]
        
        (tlr/fireEvent.click register-link)
        
        (let [register-btn (tlr/getByText container "Register")]
          (tlr/fireEvent.click register-btn)
          
          ;; Since it's inside a 'go' block, we need to wait a bit or use a timeout
          (js/setTimeout
           (fn []
             (is (= [site user pass email] @called-with)
                 "register-fn should be called with site, user, pass, and email")
             (done))
           100))))))
