(ns publicator.use-cases.test.fakes.post-queries
  (:require
   [publicator.use-cases.abstractions.post-queries :as post-q]
   [publicator.domain.aggregates.post :as post]
   [publicator.domain.aggregates.user :as user]))

(deftype GetList [db]
  post-q/GetList
  (-get-list [_]
    (->> @db
         (vals)
         (filter user/user?)
         (mapcat (fn [user]
                   (->> @db
                        (vals)
                        (filter post/post?)
                        (map #(assoc %
                                     ::user/id (:id user)
                                     ::user/full-name (:full-name user)))))))))

(defn binding-map [db]
  {#'post-q/*get-list* (->GetList db)})
