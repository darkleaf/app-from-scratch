# Запросы

```clojure
(ns publicator.persistence.post-queries
  (:require
   [hugsql.core :as hugsql]
   [hugsql.adapter.clojure-jdbc :as cj-adapter]
   [jdbc.core :as jdbc]
   [publicator.use-cases.abstractions.post-queries :as post-q]
   [publicator.domain.aggregates.post :as post]
   [publicator.domain.aggregates.user :as user]))

(hugsql/def-db-fns "publicator/persistence/post_queries.sql"
  {:adapter (cj-adapter/hugsql-adapter-clojure-jdbc)})

(defn- sql->post [raw]
  (when raw
    (let [id        (:user-id raw)
          full-name (:user-full-name raw)]
      (-> raw
          (dissoc :user-id :user-full-name)
          (assoc ::user/id id, ::user/full-name full-name)
          (update :created-at #(.toInstant %))
          (post/map->Post)))))

(deftype GetList [data-source]
  post-q/GetList
  (-get-list [this]
    (with-open [conn (jdbc/connection data-source)]
      (map sql->post (post-get-list conn)))))

(deftype GetById [data-source]
  post-q/GetById
  (-get-by-id [this id]
    (with-open [conn (jdbc/connection data-source)]
      (sql->post (post-get-by-id conn {:id id})))))

(defn binding-map [data-source]
  {#'post-q/*get-list*  (GetList. data-source)
   #'post-q/*get-by-id* (GetById. data-source)})
```


```sql
-- :name- post-get-list :? :n
SELECT "post".*,
       "user"."id" AS "user-id",
       "user"."full-name" AS "user-full-name"
FROM "post"
JOIN "user" ON "user"."posts-ids" @> ARRAY["post"."id"]

-- :name- post-get-by-id :? :1
SELECT "post".*,
       "user"."id" AS "user-id",
       "user"."full-name" AS "user-full-name"
FROM "post"
JOIN "user" ON "user"."posts-ids" @> ARRAY["post"."id"]
WHERE "post"."id" = :id
```

```clojure
(ns publicator.persistence.post-queries-test
  (:require
   [clojure.test :as t]
   [publicator.utils.test.instrument :as instument]
   [publicator.use-cases.test.factories :as factories]
   [publicator.domain.test.fakes.password-hasher :as fakes.password-hasher]
   [publicator.domain.test.fakes.id-generator :as fakes.id-generator]
   [publicator.persistence.storage :as persistence.storage]
   [publicator.persistence.storage.user-mapper :as user-mapper]
   [publicator.persistence.storage.post-mapper :as post-mapper]
   [publicator.persistence.test.db :as db]
   [publicator.use-cases.abstractions.post-queries :as post-q]
   [publicator.persistence.post-queries :as sut]
   [publicator.domain.aggregates.user :as user]))

(defn setup [t]
  (with-bindings (merge
                  (fakes.password-hasher/binding-map)
                  (fakes.id-generator/binding-map)
                  (persistence.storage/binding-map db/*data-source*
                                                   (merge
                                                    (user-mapper/mapper)
                                                    (post-mapper/mapper)))
                  (sut/binding-map db/*data-source*))
    (t)))

(t/use-fixtures :once
  instument/fixture
  db/once-fixture)

(t/use-fixtures :each
  db/each-fixture
  setup)

(defn post-with-user [post user]
  (assoc post
         ::user/id (:id user)
         ::user/full-name (:full-name user)))

(t/deftest get-list-found
  (let [post (factories/create-post)
        user (factories/create-user {:posts-ids #{(:id post)}})
        res  (post-q/get-list)
        item (first res)]
    (t/is (= 1 (count res)))
    (t/is (= (post-with-user post user)
             item))))

(t/deftest get-list-empty
  (let [res (post-q/get-list)]
    (t/is (empty? res))))

(t/deftest get-by-id
  (let [post (factories/create-post)
        id   (:id post)
        user (factories/create-user {:posts-ids #{id}})
        item (post-q/get-by-id id)]
    (t/is (= (post-with-user post user)
             item))))

(t/deftest get-by-id-not-found
  (let [item (post-q/get-by-id 42)]
    (t/is (nil? item))))
```
