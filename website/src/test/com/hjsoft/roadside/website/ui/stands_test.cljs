(ns com.hjsoft.roadside.website.ui.stands-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [helix.core :refer [$]]
            ["@testing-library/react" :as tlr]
            [com.hjsoft.roadside.website.ui.stands :as stands]
            [com.hjsoft.roadside.website.state :as state]
            [goog.object :as gobj]))

(use-fixtures :each
  {:after tlr/cleanup})

(defn render-stand-item [state stand & [props]]
  (let [ctx state/app-context]
    (tlr/render
     ($ (gobj/get ctx "Provider")
        {:value {:state state
                 :dispatch (fn [_])
                 :ui {:set-editing-stand (fn [_])
                      :set-show-form (fn [_])}}}
        ($ stands/stand-item
           {:stand stand
            :on-delete (:on-delete props)
            :selected? (:selected? props)
            :on-edit (:on-edit props)
            :on-click (:on-click props)})))))

(deftest stand-item-ownership-test
  (testing "Edit and Delete buttons are visible when user is owner"
    (let [state {:settings {:user "alice"}}
          stand {:id "s1" :name "Alice's Stand" :creator "alice"}
          res (render-stand-item state stand)
          container (.-container res)]
      (is (some? (tlr/queryByText container "Edit")) "Edit button should be visible")
      (is (some? (tlr/queryByText container "Delete")) "Delete button should be visible")))

  (testing "Edit and Delete buttons are HIDDEN when user is NOT owner"
    (let [state {:settings {:user "bob"}}
          stand {:id "s1" :name "Alice's Stand" :creator "alice"}
          res (render-stand-item state stand)
          container (.-container res)]
      (is (nil? (tlr/queryByText container "Edit")) "Edit button should be hidden")
      (is (nil? (tlr/queryByText container "Delete")) "Delete button should be hidden")))

  (testing "Edit and Delete buttons are visible when NO creator is set (local stand)"
    (let [state {:settings {:user "bob"}}
          stand {:id "s1" :name "Local Stand"} ;; No creator
          res (render-stand-item state stand)
          container (.-container res)]
      (is (some? (tlr/queryByText container "Edit")) "Edit button should be visible for local stand")
      (is (some? (tlr/queryByText container "Delete")) "Delete button should be visible for local stand")))

  (testing "Edit and Delete buttons are visible when NO user and NO creator (initial state)"
    (let [state {:settings {}}
          stand {:id "s1" :name "Initial Stand"}
          res (render-stand-item state stand)
          container (.-container res)]
      (is (some? (tlr/queryByText container "Edit"))
        "Edit button should be visible when both unset")
      (is (some? (tlr/queryByText container "Delete"))
        "Delete button should be visible when both unset")))

  (testing "Delete button changes to 'Really?' when clicked"
    (let [state {:settings {:user "alice"}}
          stand {:id "s1" :name "Alice's Stand" :creator "alice"}
          res (render-stand-item state stand)
          container (.-container res)
          delete-btn (tlr/queryByText container "Delete")]
      (is (some? delete-btn) "Delete button should be initially visible")
      (tlr/fireEvent.click delete-btn)
      (is (nil? (tlr/queryByText container "Delete")) "Delete button should be hidden after click")
      (is (some? (tlr/queryByText container "Really?")) "Really? button should be visible after click")))

  (testing "Clicking 'Really?' calls on-delete"
    (let [state {:settings {:user "alice"}}
          stand {:id "s1" :name "Alice's Stand" :creator "alice"}
          deleted-stand (atom nil)
          res (render-stand-item state stand {:on-delete #(reset! deleted-stand %)})
          container (.-container res)]
      (tlr/fireEvent.click (tlr/queryByText container "Delete"))
      (tlr/fireEvent.click (tlr/queryByText container "Really?"))
      (is (= stand @deleted-stand) "on-delete should be called with the stand"))))

(deftest stand-item-incomplete-test
  (testing "incomplete-stand class is applied when name and products are missing"
    (let [state {:settings {:user "alice"}}
          stand {:id "s1" :name "" :products [] :creator "alice"}
          res (render-stand-item state stand)
          container (.-container res)
          item-div (.querySelector container ".stand-item")]
      (is (.contains (.-classList item-div) "incomplete-stand")
          "Should have incomplete-stand class")
      (is (tlr/queryByText item-div "(no details)")
        "the item shows (no details)")))

  (testing "incomplete-stand class is NOT applied when name is present"
    (let [state {:settings {:user "alice"}}
          stand {:id "s1" :name "My Stand" :products [] :creator "alice"}
          res (render-stand-item state stand)
          container (.-container res)
          item-div (.querySelector container ".stand-item")]
      (is (not (.contains (.-classList item-div) "incomplete-stand"))
          "Should NOT have incomplete-stand class")
      (is (not (tlr/queryByText item-div "(no details)"))) ))

  (testing "incomplete-stand class is NOT applied when products are present"
    (let [state {:settings {:user "alice"}}
          stand {:id "s1" :name "" :products ["Apples"] :creator "alice"}
          res (render-stand-item state stand)
          container (.-container res)
          item-div (.querySelector container ".stand-item")]
      (is (not (.contains (.-classList item-div) "incomplete-stand"))
          "Should NOT have incomplete-stand class")
      (is (not (tlr/queryByText item-div "(no details)")))))

  (testing "incomplete-stand class is NOT applied when NOT owner"
    (let [state {:settings {:user "bob"}}
          stand {:id "s1" :name "" :products [] :creator "alice"}
          res (render-stand-item state stand)
          container (.-container res)
          item-div (.querySelector container ".stand-item")]
      (is (not (.contains (.-classList item-div) "incomplete-stand"))
          "Should NOT have incomplete-stand class when not owner")
      (is (tlr/queryByText item-div "(no details)")
        "the item shows (no details)"))))
