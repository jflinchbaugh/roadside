(ns com.hjsoft.mapmarks.website.ui.marks
  (:require [helix.core :refer [defnc $ <>]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.mapmarks.website.utils :as utils]
            [com.hjsoft.mapmarks.website.domain.mark :as mark-domain]
            [com.hjsoft.mapmarks.website.state :as state]
            [clojure.string :as str]
            [com.hjsoft.mapmarks.website.ui.hooks :as ui-hooks]
            [com.hjsoft.mapmarks.website.ui.layout :refer [mark-notification-toast]]))

(def icon-up-arrow "\u25B2")
(def icon-down-arrow "\u25BC")

(defnc mark-voting
  [{:keys [mark on-vote]}]
  (let [{:keys [score user-vote]} mark
        score (or score 0)
        user-vote (or user-vote 0)]
    (d/div
     {:class "mark-voting"
      :onClick #(.stopPropagation %)}
     (d/button
      {:class (str "vote-btn upvote" (when (= user-vote 1) " active"))
       :onClick #(on-vote 1)
       :title "Upvote"}
      icon-up-arrow)
     (d/span
      {:class (str "vote-score"
                   (cond (pos? score) " positive"
                         (neg? score) " negative"
                         :else ""))}
      score)
     (d/button
      {:class (str "vote-btn downvote" (when (= user-vote -1) " active"))
       :onClick #(on-vote -1)
       :title "Downvote"}
      icon-down-arrow))))

(defnc mark-item
  [{:keys [mark selected? on-click on-edit on-delete on-vote item-ref]}]
  (let [[confirming? set-confirming] (hooks/use-state false)
        app-state (state/use-app-state)
        current-user (get-in app-state [:settings :user])
        creator (:creator mark)
        owner? (or (empty? (str creator))
                   (= (str current-user) (str creator)))
        expired? (utils/past-expiration? (:expiration mark))
        incomplete? (and owner?
                         (empty? (str/trim (or (:name mark) "")))
                         (empty? (:tags mark)))]
    (hooks/use-effect
     [selected?]
     (when-not selected?
       (set-confirming false)))
    (d/div
     {:key (mark-domain/mark-key mark)
      :ref item-ref
      :class (str
              "mark-item"
              (when selected? " selected-mark")
              (when expired? " expired-mark")
              (when incomplete? " incomplete-mark"))
      :onClick on-click}
     ($ mark-notification-toast {:mark-id (:id mark)})
     (d/div
      {:class "mark-content"}
      (when (and (not (seq (:name mark))) (not (seq (:tags mark))))
        (d/div
         {:class "mark-incomplete"}
         "(no details)"))
      (when (seq (:name mark))
        (d/div
         {:class "mark-header"}
         (d/h4 (:name mark))))
      (when (seq (:address mark))
        (d/p (:address mark)))
      (let [town-state (remove empty? [(:town mark) (:state mark)])]
        (when (seq town-state)
          (d/p (str/join ", " town-state))))
      (when (seq (:tags mark))
        (d/div
         {:class "mark-tags"}
         (d/strong (str (:tags-name-plural (:config app-state)) ": "))
         (d/div
          {:class "tags-tags"}
          (map (fn [tag]
                 (d/span
                  {:key tag
                   :class "tag-tag"}
                  tag))
               (filter string? (:tags mark))))))
      (when (seq (:notes mark))
        (d/p
         {:class "mark-notes"}
         (d/strong "Notes: ")
         (:notes mark)))
      (when selected?
        (d/div
         {:class "mark-extra-info"}
         ($ mark-voting {:mark mark :on-vote on-vote})
         (d/div
          {:class "mark-extra-fields"}
          (when (and (:lat mark) (:lon mark))
            (d/p {:class "coordinate-text"} (str (:lat mark) ", " (:lon mark))))
          (when (seq (:expiration mark))
            (d/p
             {:class "expiration-date"}
             (d/strong (if expired? "Expired: " "Expires: "))
             (:expiration mark)))
          (when (seq (:updated mark))
            (d/p
             {:class "mark-updated"}
             (d/strong "Last Updated: ")
             (utils/format-timestamp (:updated mark))))
          (when (seq (:creator mark))
            (d/p
             {:class "mark-creator"}
             (d/strong "Created By: ")
             (:creator mark)))
          (d/p
           {:class "mark-shared"}
           (d/strong "Shared: ")
           (if (:shared? mark) "Yes" "No"))))))
     (d/div
      {:class "mark-actions"}

      (when-let [map-link (utils/make-map-link (:lat mark) (:lon mark))]
        (d/a {:href map-link
              :target "_blank"
              :rel "noopener noreferrer"
              :class "go-mark-btn"}
             "Go"))
      (when owner?
        (let [handle-edit (fn [e]
                            (.stopPropagation e)
                            (set-confirming false)
                            (on-edit mark))]
          (d/button
           {:class "edit-mark-btn"
            :onClick handle-edit
            :title (str "Edit this " (str/lower-case (:mark-name-singular (:config app-state))))}
           "Edit")))
      (when owner?
        (if confirming?
          (let [handle-delete (fn [e]
                                (.stopPropagation e)
                                (on-delete mark))]
            (d/button
             {:class "delete-mark-btn"
              :onClick handle-delete
              :title (str "Really delete this " (str/lower-case (:mark-name-singular (:config app-state))) "?")}
             "Really?"))
          (let [handle-delete-confirm (fn [e]
                                        (.stopPropagation e)
                                        (set-confirming true))]
            (d/button
             {:class "delete-mark-btn"
              :onClick handle-delete-confirm
              :title (str "Delete this " (str/lower-case (:mark-name-singular (:config app-state))))}
             "Delete"))))))))

(defnc marks-list
  [{:keys [marks]}]
  (let [app-state (state/use-app-state)
        selected-mark (:selected-mark app-state)
        dispatch (state/use-dispatch)
        {:keys [set-editing-mark set-show-form]} (state/use-ui)
        {:keys [delete-mark! vote-mark!]} (ui-hooks/use-actions)
        mark-refs (hooks/use-ref {})]
    (hooks/use-effect
     [selected-mark]
     (when selected-mark
       (when-let [mark-el (get @mark-refs (mark-domain/mark-key selected-mark))]
         (.scrollIntoView
          mark-el
          (clj->js {:behavior "smooth" :block "nearest"})))))

    (d/div
     {:class "marks-list"}
     (if (empty? marks)
       (d/p (str "No " (str/lower-case (:mark-name-plural (:config app-state))) " added yet."))
       (<>
        (map
         (fn [mark]
           (let [key (mark-domain/mark-key mark)]
             ($ mark-item
                {:key key
                 :mark mark
                 :selected? (= key (mark-domain/mark-key selected-mark))
                 :on-click #(dispatch [:set-selected-mark mark])
                 :on-edit #(do
                             (set-editing-mark %)
                             (set-show-form true))
                 :on-delete #(delete-mark! %)
                 :on-vote #(vote-mark! mark %)
                 :item-ref (fn [el] (swap! mark-refs assoc key el))})))
         marks))))))

(defnc tag-list
  [{:keys [marks]}]
  (let [app-state (state/use-app-state)
        {:keys [tag-filter show-expired? config]} app-state
        dispatch (state/use-dispatch)
        unique-tags (hooks/use-memo
                         [marks]
                         (utils/get-all-unique-tags marks))]
    (d/div
     {:class "tag-list"}
     (d/div
      {:class "tag-list-content"}
      (d/strong (str (:tags-name-plural config) ": "))
      (if (empty? unique-tags)
        (d/p (str "No " (str/lower-case (:tags-name-plural config)) " available yet."))
        (d/div
         {:class "tags-tags"}
         (map (fn [tag]
                (d/span
                 {:key tag
                  :class (str
                          "tag-tag"
                          (when (= tag tag-filter)
                            " tag-tag-active"))
                  :onClick #(if (= tag tag-filter)
                              (dispatch [:set-tag-filter nil])
                              (dispatch [:set-tag-filter tag]))}
                 tag))
              unique-tags)))
      (d/div
      {:class "filter-controls"}
      (when (pos? (or (:default-expiration-days config) 0))
        (d/span
         {:class (str "tag-tag show-expired-toggle"
                      (when show-expired? " tag-tag-active"))
          :onClick #(dispatch [:set-show-expired (not show-expired?)])}
         (str "Show Expired " (:mark-name-plural config))))
      (when tag-filter
        (d/button
         {:class "clear-filter-btn"
          :onClick #(dispatch [:set-tag-filter nil])}
         "Clear Filter")))))))

