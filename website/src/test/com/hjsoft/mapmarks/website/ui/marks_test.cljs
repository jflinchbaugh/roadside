(ns com.hjsoft.mapmarks.website.ui.marks-test
  (:require [cljs.test :as t]
            [helix.core :refer [$]]
            ["@testing-library/react" :as tlr]
            [com.hjsoft.mapmarks.website.ui.marks :as marks]
            [com.hjsoft.mapmarks.website.state :as state]
            [com.hjsoft.mapmarks.website.config :as config]
            [goog.object :as gobj]))

(t/use-fixtures :each
  {:after tlr/cleanup})

(defn render-mark-item [state mark & [props]]
  (let [ctx state/app-context
        state-with-config (update state :config #(merge config/config %))]
    (tlr/render
     ($ (gobj/get ctx "Provider")
        {:value {:state state-with-config
                 :dispatch (fn [_])
                 :ui {:set-editing-mark (fn [_])
                      :set-show-form (fn [_])}}}
        ($ marks/mark-item
           {:mark mark
            :on-delete (:on-delete props)
            :selected? (:selected? props)
            :on-edit (:on-edit props)
            :on-extend (:on-extend props)
            :review-mode? (:review-mode? props)
            :on-click (:on-click props)})))))

(t/deftest mark-item-ownership-test
  (t/testing "Edit and Delete buttons are visible when user is owner"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)]
      (t/is (some? (tlr/queryByText container "Edit")) "Edit button should be visible")
      (t/is (some? (tlr/queryByText container "Delete")) "Delete button should be visible")))

  (t/testing "Edit and Delete buttons are HIDDEN when user is NOT owner"
    (let [state {:settings {:user "bob"}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)]
      (t/is (nil? (tlr/queryByText container "Edit")) "Edit button should be hidden")
      (t/is (nil? (tlr/queryByText container "Delete")) "Delete button should be hidden")))

  (t/testing "Edit and Delete buttons are visible when NO creator is set (local mark)"
    (let [state {:settings {:user "bob"}}
          mark {:id "s1" :name "Local Mark"} ;; No creator
          res (render-mark-item state mark)
          container (.-container res)]
      (t/is (some? (tlr/queryByText container "Edit")) "Edit button should be visible for local mark")
      (t/is (some? (tlr/queryByText container "Delete")) "Delete button should be visible for local mark")))

  (t/testing "Edit and Delete buttons are visible when NO user and NO creator (initial state)"
    (let [state {:settings {}}
          mark {:id "s1" :name "Initial Mark"}
          res (render-mark-item state mark)
          container (.-container res)]
      (t/is (some? (tlr/queryByText container "Edit"))
        "Edit button should be visible when both unset")
      (t/is (some? (tlr/queryByText container "Delete"))
        "Delete button should be visible when both unset")))

  (t/testing "Delete button changes to 'Really?' when clicked"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)]
      (let [delete-btn (tlr/queryByText container "Delete")]
        (t/is (some? delete-btn) "Delete button should be initially visible")
        (tlr/fireEvent.click delete-btn)
        (t/is (nil? (tlr/queryByText container "Delete")) "Delete button should be hidden after click")
        (t/is (some? (tlr/queryByText container "Really?")) "Really? button should be visible after click"))))

  (t/testing "Clicking 'Really?' calls on-delete"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice"}
          deleted-mark (atom nil)
          res (render-mark-item state mark {:on-delete #(reset! deleted-mark %)})
          container (.-container res)]
      (tlr/fireEvent.click (tlr/queryByText container "Delete"))
      (tlr/fireEvent.click (tlr/queryByText container "Really?"))
      (t/is (= mark @deleted-mark) "on-delete should be called with the mark"))))

(t/deftest mark-item-incomplete-test
  (t/testing "incomplete-mark class is applied when name and tags are missing"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "" :tags [] :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)
          item-div (.querySelector container ".mark-item")]
      (t/is (.contains (.-classList item-div) "incomplete-mark")
          "Should have incomplete-mark class")
      (t/is (tlr/queryByText item-div "(no details)")
        "the item shows (no details)")))

  (t/testing "incomplete-mark class is NOT applied when name is present"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "My Mark" :tags [] :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)
          item-div (.querySelector container ".mark-item")]
      (t/is (not (.contains (.-classList item-div) "incomplete-mark"))
          "Should NOT have incomplete-mark class")
      (t/is (not (tlr/queryByText item-div "(no details)")))))

  (t/testing "incomplete-mark class is NOT applied when tags are present"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "" :tags ["Apples"] :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)
          item-div (.querySelector container ".mark-item")]
      (t/is (not (.contains (.-classList item-div) "incomplete-mark"))
          "Should NOT have incomplete-mark class")
      (t/is (not (tlr/queryByText item-div "(no details)")))))

  (t/testing "incomplete-mark class is NOT applied when NOT owner"
    (let [state {:settings {:user "bob"}}
          mark {:id "s1" :name "" :tags [] :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)
          item-div (.querySelector container ".mark-item")]
      (t/is (not (.contains (.-classList item-div) "incomplete-mark"))
          "Should NOT have incomplete-mark class when not owner")
      (t/is (tlr/queryByText item-div "(no details)")
        "the item shows (no details)"))))

(t/deftest mark-item-voting-visibility-test
  (t/testing "Voting widget is visible when mark is selected"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice" :score 5}
          res (render-mark-item state mark {:selected? true})
          container (.-container res)]
      (t/is (some? (.querySelector container ".mark-voting"))
          "Voting widget should be visible when selected")
      (t/is (tlr/queryByText container "5") "Score should be visible")))

  (t/testing "Voting widget is HIDDEN when mark is NOT selected"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice" :score 5}
          res (render-mark-item state mark {:selected? false})
          container (.-container res)]
      (t/is (nil? (.querySelector container ".mark-voting"))
          "Voting widget should be hidden when not selected"))))

(t/deftest mark-item-anchor-id-test
  (t/testing "Anchor ID is set on mark-item element to match RSS format"
    (let [state {:settings {:user "alice"}
                 :config {:mark-name-singular "Stand"}}
          mark {:id "xyz-123" :name "Apple Stand" :creator "alice"}
          res (render-mark-item state mark)
          container (.-container res)
          item-div (.querySelector container ".mark-item")]
      (t/is (= "stand=xyz-123" (.getAttribute item-div "id"))
          "The ID of the mark-item element should match the RSS anchor id"))))

(t/deftest mark-item-review-mode-test
  (t/testing "Extend button is visible in review mode for owned marks"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice" :expiration "2026-08-01"}
          res (render-mark-item state mark {:review-mode? true})
          container (.-container res)]
      (t/is (some? (tlr/queryByText container "Extend (+30d)"))
          "Extend button should be visible in review mode")
      (t/is (some? (tlr/queryByText container "Edit"))
          "Edit button should still be visible in review mode")))

  (t/testing "Extend button is NOT visible when not in review mode"
    (let [state {:settings {:user "alice"}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice" :expiration "2026-08-01"}
          res (render-mark-item state mark {:review-mode? false})
          container (.-container res)]
      (t/is (nil? (tlr/queryByText container "Extend (+30d)"))
          "Extend button should NOT be visible when not in review mode")))

  (t/testing "Extend button uses configured extend-expiration-days"
    (let [state {:settings {:user "alice"}
                 :config {:extend-expiration-days 14}}
          mark {:id "s1" :name "Alice's Mark" :creator "alice"
                :expiration "2026-08-01"}
          res (render-mark-item state mark {:review-mode? true})
          container (.-container res)]
      (t/is (some? (tlr/queryByText container "Extend (+14d)"))
          "Extend button should show configured days"))))
