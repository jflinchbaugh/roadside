(ns com.hjsoft.roadside.website.ui.stands
  (:require [helix.core :refer [defnc]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            [com.hjsoft.roadside.website.utils :as utils]
            [clojure.string :as str]))

(defnc stands-list
  [{:keys
    [stands
     set-stands
     set-editing-stand
     set-form-data
     set-show-form
     selected-stand
     set-selected-stand]}]
  (let [stand-refs (hooks/use-ref {})]
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
       (map
        (fn [stand]
          (d/div
           {:key (utils/stand-key stand)
            :ref (fn [el] (swap! stand-refs assoc (utils/stand-key stand) el))
            :class (str
                    "stand-item"
                    (when (= (utils/stand-key stand) (utils/stand-key selected-stand))
                      " selected-stand"))
            :onClick #(set-selected-stand stand)}
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
                     (:products stand)))))
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
              :onClick #(do (set-editing-stand stand)
                            (set-form-data
                             (assoc stand
                                    :town (:town stand)
                                    :state (:state stand)
                                    :address (:address stand)
                                    :notes (:notes stand)
                                    :shared? (:shared? stand)))
                            (set-show-form true))
              :title "Edit this stand"}
             "Edit")
            (d/button
             {:class "delete-stand-btn"
              :onClick #(set-stands (fn [current-stands]
                                      (->> current-stands
                                           (remove #{stand})
                                           vec)))
              :title "Delete this stand"}
             "Delete"))))
        stands)))))

(defnc product-list
  [{:keys [stands
           set-product-filter
           product-filter]}]
  (let [all-products (flatten (map :products stands))
        unique-products (sort (distinct all-products))]
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
                             (set-product-filter nil)
                             (set-product-filter product))}
                product))
             unique-products)))
     (when product-filter
       (d/button
        {:class "clear-filter-btn"
         :onClick #(set-product-filter nil)}
        "Clear Filter")))))
