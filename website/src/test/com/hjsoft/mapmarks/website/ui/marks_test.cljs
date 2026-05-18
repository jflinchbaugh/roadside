(ns com.hjsoft.mapmarks.website.ui.marks-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [helix.core :refer [$]]
            ["@testing-library/react" :as tlr]
            [com.hjsoft.mapmarks.website.ui.marks :as marks]
            [com.hjsoft.mapmarks.website.state :as state]
            [goog.object :as gobj]))

(use-fixtures :each
  {:after tlr/cleanup})

(defn render-mark-item [state mark & [props]]
  (let [ctx state/app-context]
    (tlr/render
     ($ (gobj/get ctx "Provider")
        {:value {:state state
                 :dispatch (fn [_])
                 :ui {:set-editing-mark (fn [_])
                      :set-show-form (fn [_])}}}
        ($ marks/mark-item
           {:mark mark
            :on-delete (:on-delete props)
            :selected? (:selected? props)
            :on-edit (:on-edit props)
            :on-click (:on-click props)})))))

(deftest mark-item-ownership-test
  (testing "Edit and Delete buttons are visible when user is owner"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)]
      (is (some? (tlr/queryByText container "Edit")) "Edit button should be visible")
      (is (some? (tlr/queryByText container "Delete")) "Delete button should be visible")))

  (testing "Edit and Delete buttons are HIDDEN when user is NOT owner"
    (let [state {:settings {:user "bob"}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)]
      (is (nil? (tlr/queryByText container "Edit")) "Edit button should be hidden")
      (is (nil? (tlr/queryByText container "Delete")) "Delete button should be hidden")))

  (testing "Edit and Delete buttons are visible when NO creator is set (local mark)"
    (let [state {:settings {:user "bob"}}
          mark {:id "s1" :name "Local Mark"} ;; No creator
          res (render-mark-item state mark)
          container (.-container res)]
      (is (some? (tlr/queryByText container "Edit")) "Edit button should be visible for local mark")
      (is (some? (tlr/queryByText container "Delete")) "Delete button should be visible for local mark")))

  (testing "Edit and Delete buttons are visible when NO user and NO creator (initial state)"
    (let [state {:settings {}}
          mark {:id "s1" :name "Initial Mark"}
          res (render-mark-item state mark)
          container (.-container res)]
      (is (some? (tlr/queryByText container "Edit"))
        "Edit button should be visible when both unset")
      (is (some? (tlr/queryByText container "Delete"))
        "Delete button should be visible when both unset")))

  (testing "Delete button changes to 'Really?' when clicked"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)
          delete-btn (tlr/queryByText container "Delete")]
      (is (some? delete-btn) "Delete button should be initially visible")
      (tlr/fireEvent.click delete-btn)
      (is (nil? (tlr/queryByText container "Delete")) "Delete button should be hidden after click")
      (is (some? (tlr/queryByText container "Really?")) "Really? button should be visible after click")))

  (testing "Clicking 'Really?' calls on-delete"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice"}
          deleted-mark (atom nil)
          res (render-mark-item state mark {:on-delete #(reset! deleted-mark %)})
          container (.-container res)]
      (tlr/fireEvent.click (tlr/queryByText container "Delete"))
      (tlr/fireEvent.click (tlr/queryByText container "Really?"))
      (is (= mark @deleted-mark) "on-delete should be called with the mark"))))

(deftest mark-item-incomplete-test
  (testing "incomplete-mark class is applied when name and tags are missing"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "" :tags [] :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)
          item-div (.querySelector container ".mark-item")]
      (is (.contains (.-classList item-div) "incomplete-mark")
          "Should have incomplete-mark class")
      (is (tlr/queryByText item-div "(no details)")
        "the item shows (no details)")))

  (testing "incomplete-mark class is NOT applied when name is present"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "My Mark" :tags [] :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)
          item-div (.querySelector container ".mark-item")]
      (is (not (.contains (.-classList item-div) "incomplete-mark"))
          "Should NOT have incomplete-mark class")
      (is (not (tlr/queryByText item-div "(no details)"))) ))

  (testing "incomplete-mark class is NOT applied when tags are present"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "" :tags ["Apples"] :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)
          item-div (.querySelector container ".mark-item")]
      (is (not (.contains (.-classList item-div) "incomplete-mark"))
          "Should NOT have incomplete-mark class")
      (is (not (tlr/queryByText item-div "(no details)")))))

  (testing "incomplete-mark class is NOT applied when NOT owner"
    (let [state {:settings {:user "bob"}}
          mark {:id "s1" :name "" :tags [] :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)
          item-div (.querySelector container ".mark-item")]
      (is (not (.contains (.-classList item-div) "incomplete-mark"))
          "Should NOT have incomplete-mark class when not owner")
      (is (tlr/queryByText item-div "(no details)")
        "the item shows (no details)"))))

(deftest mark-item-voting-visibility-test
  (testing "Voting widget is visible when mark is selected"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice" :score 5}
          res (render-mark-item state mark {:selected? true})
          container (.-container res)]
      (is (some? (.querySelector container ".mark-voting"))
          "Voting widget should be visible when selected")
      (is (tlr/queryByText container "5") "Score should be visible")))

  (testing "Voting widget is HIDDEN when mark is NOT selected"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice" :score 5}
          res (render-mark-item state mark {:selected? false})
          container (.-container res)]
      (is (nil? (.querySelector container ".mark-voting"))
          "Voting widget should be hidden when not selected"))))
