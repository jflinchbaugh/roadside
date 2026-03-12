(ns com.hjsoft.roadside.website.ui.stands
  (:require [helix.core :refer [defnc $]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.domain.stand :as stand-domain]
            [com.hjsoft.roadside.website.state :as state]
            [clojure.string :as str]
            [com.hjsoft.roadside.website.ui.hooks :as ui-hooks]
            [com.hjsoft.roadside.website.ui.layout :refer [stand-notification-toast]]))

(defnc stand-item
  [{:keys [stand selected? on-click on-edit on-delete item-ref]}]
  (let [app-state (state/use-app-state)
        current-user (get-in app-state [:settings :user])
        creator (:creator stand)
        owner? (or (empty? (str creator))
                   (= (str current-user) (str creator)))
        expired? (utils/past-expiration? (:expiration stand))
        incomplete? (and owner?
                         (empty? (str/trim (or (:name stand) "")))
                         (empty? (:products stand)))]
    (d/div
     {:key (stand-domain/stand-key stand)
      :ref item-ref
      :class (str
              "stand-item"
              (when selected? " selected-stand")
              (when expired? " expired-stand")
              (when incomplete? " incomplete-stand"))
      :onClick on-click}
     ($ stand-notification-toast {:stand-id (:id stand)})
     (d/div
      {:class "stand-content"}
      (when (and (not (seq (:name stand))) (not (seq (:products stand))))
        (d/div
          {:class "stand-incomplete"}
          "(no details)"))
      (when (seq (:name stand))
        (d/div
         {:class "stand-header"}
         (d/h4 (:name stand))))
      (when (seq (:coordinate stand))
        (d/p {:class "coordinate-text"} (:coordinate stand)))
      (when (seq (:address stand))
        (d/p (:address stand)))
      (let [town-state (remove empty? [(:town stand) (:state stand)])]
        (when (seq town-state)
          (d/p (str/join ", " town-state))))
      (when (seq (:products stand))
        (d/div
         {:class "stand-products"}
         (d/strong "Products: ")
         (d/div
          {:class "products-tags"}
          (map (fn [product]
                 (d/span
                  {:key product
                   :class "product-tag"}
                  product))
               (filter string? (:products stand))))))
      (when (seq (:notes stand))
        (d/p
         {:class "stand-notes"}
         (d/strong "Notes: ")
         (:notes stand)))
      (when (seq (:expiration stand))
        (d/p
         {:class "expiration-date"}
         (d/strong (if expired? "Expired: " "Expires: "))
         (:expiration stand)))
      (when (seq (:updated stand))
        (d/p
         {:class "stand-updated"}
         (d/strong "Last Updated: ")
         (:updated stand)))
      (when (seq (:creator stand))
        (d/p
         {:class "stand-creator"}
         (d/strong "Created By: ")
         (:creator stand)))
      (d/p
       {:class "stand-shared"}
       (d/strong "Shared: ")
       (if (:shared? stand) "Yes" "No")))
     (d/div
      {:class "stand-actions"}
      (when-let [map-link (utils/make-map-link (:coordinate stand))]
        (d/a {:href map-link
              :target "_blank"
              :rel "noopener noreferrer"
              :class "go-stand-btn"}
             "Go"))
      (when owner?
        (d/button
         {:class "edit-stand-btn"
          :onClick (fn [e]
                     (.stopPropagation e)
                     (on-edit stand))
          :title "Edit this stand"}
         "Edit"))
      (when owner?
        (d/button
         {:class "delete-stand-btn"
          :onClick (fn [e]
                     (.stopPropagation e)
                     (on-delete stand))
          :title "Delete this stand"}
         "Delete"))))))

(defnc stands-list
  [{:keys [stands]}]
  (let [app-state (state/use-app-state)
        selected-stand (:selected-stand app-state)
        dispatch (state/use-dispatch)
        {:keys [set-editing-stand set-show-form]} (state/use-ui)
        {:keys [delete-stand!]} (ui-hooks/use-actions)
        stand-refs (hooks/use-ref {})]
    (hooks/use-effect
     [selected-stand]
     (when selected-stand
       (when-let [stand-el (get @stand-refs (stand-domain/stand-key selected-stand))]
         (.scrollIntoView
          stand-el
          (clj->js {:behavior "smooth" :block "nearest"})))))

    (d/div
     {:class "stands-list"}
     (if (empty? stands)
       (d/p "No stands added yet.")
       (d/div
        (map
         (fn [stand]
           (let [key (stand-domain/stand-key stand)]
             ($ stand-item
                {:key key
                 :stand stand
                 :selected? (= key (stand-domain/stand-key selected-stand))
                 :on-click #(dispatch [:set-selected-stand stand])
                 :on-edit #(do
                             (set-editing-stand %)
                             (set-show-form true))
                 :on-delete #(delete-stand! %)
                 :item-ref (fn [el] (swap! stand-refs assoc key el))})))
         stands))))))

(defnc product-list
  [{:keys [stands]}]
  (let [{:keys [product-filter show-expired?]} (state/use-app-state)
        dispatch (state/use-dispatch)
        unique-products (hooks/use-memo
                         [stands]
                         (utils/get-all-unique-products stands))]
    (d/div
     {:class "product-list"}
     (d/div
      {:class "product-list-content"}
      (if (empty? unique-products)
        (d/p "No products available yet.")
        (d/div
         {:class "products-tags"}
         (map (fn [product]
                (d/span
                 {:key product
                  :class (str
                          "product-tag"
                          (when (= product product-filter)
                            " product-tag-active"))
                  :onClick #(if (= product product-filter)
                              (dispatch [:set-product-filter nil])
                              (dispatch [:set-product-filter product]))}
                 product))
              unique-products)))
      (d/div
       {:class "filter-controls"}
       (d/span
        {:class (str "product-tag show-expired-toggle"
                     (when show-expired? " product-tag-active"))
         :onClick #(dispatch [:set-show-expired (not show-expired?)])}
        "Show Expired")
       (when product-filter
         (d/button
          {:class "clear-filter-btn"
           :onClick #(dispatch [:set-product-filter nil])}
          "Clear Filter")))))))
