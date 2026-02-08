(ns com.hjsoft.roadside.website.ui.stands
  (:require [helix.core :refer [defnc]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.utils :as utils]
            [com.hjsoft.roadside.website.state :as state]
            [clojure.string :as str]))

(defnc stands-list
  [{:keys [stands]}]
  (let [{:keys [state dispatch]} (hooks/use-context state/app-context)
        {:keys [selected-stand]} state
        stand-refs (hooks/use-ref {})]
    (hooks/use-effect
     [selected-stand]
     (when selected-stand
       (when-let [stand-el (get @stand-refs (utils/stand-key selected-stand))]
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
           (d/div
            {:key (utils/stand-key stand)
             :ref (fn [el] (swap! stand-refs assoc (utils/stand-key stand) el))
             :class (str
                     "stand-item"
                     (when (= (utils/stand-key stand) (utils/stand-key selected-stand))
                       " selected-stand"))
             :onClick #(dispatch [:set-selected-stand stand])}
            (d/div
             {:class "stand-content"}
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
                (d/strong "Expires: ")
                (:expiration stand)))
             (when (seq (:updated stand))
               (d/p
                {:class "stand-updated"}
                (d/strong "Last Updated: ")
                (:updated stand)))
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
             (d/button
              {:class "edit-stand-btn"
               :onClick #(do (dispatch [:set-editing-stand stand])
                             (dispatch [:set-stand-form-data
                                        (assoc stand
                                               :town (:town stand)
                                               :state (:state stand)
                                               :address (:address stand)
                                               :notes (:notes stand)
                                               :shared? (:shared? stand))])
                             (dispatch [:set-show-form true]))
               :title "Edit this stand"}
              "Edit")
             (d/button
              {:class "delete-stand-btn"
               :onClick #(dispatch [:set-stands (fn [current-stands]
                                                 (->> current-stands
                                                      (remove #{stand})
                                                      vec))])
               :title "Delete this stand"}
              "Delete"))))
         stands))))))

(defnc product-list
  [{:keys [stands]}]
  (let [{:keys [state dispatch]} (hooks/use-context state/app-context)
        {:keys [product-filter]} state
        all-products (flatten (map :products stands))
        unique-products (->> all-products
                             (filter string?)
                             distinct
                             sort)]
    (d/div
     {:class "product-list"}
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
     (when product-filter
       (d/button
        {:class "clear-filter-btn"
         :onClick #(dispatch [:set-product-filter nil])}
        "Clear Filter")))))
