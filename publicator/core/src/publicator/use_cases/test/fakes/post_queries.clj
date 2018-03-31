(ns publicator.use-cases.test.fakes.post-queries
  (:require
   [publicator.use-cases.abstractions.post-queries :as post-q]
   [publicator.domain.aggregates.post :as post]
   [publicator.domain.aggregates.user :as user]
   [publicator.domain.services.user-posts :as user-posts]))

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

(deftype GetById [db]
  post-q/GetById
  (-get-by-id [_ id]
    (when-some [post (get @db id)]
      (prn (->> @db
                (vals)
                (filter user/user?)
                (filter #(user-posts/author? % post))))


      (let [user (->> @db
                      (vals)
                      (filter user/user?)
                      (filter #(user-posts/author? % post))
                      (first))]
        (assoc post
               ::user/id (:id user)
               ::user/full-name (:full-name user))))))

(defn binding-map [db]
  {#'post-q/*get-list* (->GetList db)
   #'post-q/*get-by-id* (->GetById db)})
