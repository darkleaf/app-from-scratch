(ns publicator.use-cases.test.fakes.post-queries
  (:require
   [publicator.use-cases.abstractions.post-queries :as post-q]
   [publicator.domain.aggregates.post :as post]
   [publicator.domain.aggregates.user :as user]
   [publicator.domain.services.user-posts :as user-posts]))

(defn- author-for-post [db post]
  (->> @db
       (vals)
       (filter user/user?)
       (filter #(user-posts/author? % post))
       (first)))

(defn- assoc-user-fields [post user]
  (assoc post
         ::user/id (:id user)
         ::user/full-name (:full-name user)))

(deftype GetList [db]
  post-q/GetList
  (-get-list [_]
    (->> @db
         (vals)
         (filter post/post?)
         (map #(when-some [author (author-for-post db %)]
                 (assoc-user-fields % author)))
         (remove nil?))))

(deftype GetById [db]
  post-q/GetById
  (-get-by-id [_ id]
    (when-some [post (get @db id)]
      (when-some [author (author-for-post db post)]
        (assoc-user-fields post author)))))

(defn binding-map [db]
  {#'post-q/*get-list* (->GetList db)
   #'post-q/*get-by-id* (->GetById db)})
