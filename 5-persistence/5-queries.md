# Запросы

Для примера рассмотрим запросы для агрегата Пост, а именно
получение списка постов и поста по идентификатору.
При этом пост должен содержать дополнительные аттрибуты:
идентификатор и полное имя автора.

Вот абстракция:

```clojure
(ns publicator.use-cases.abstractions.post-queries
  (:require
   [publicator.domain.aggregates.user :as user]
   [publicator.domain.aggregates.post :as post]
   [clojure.spec.alpha :as s]))

(defprotocol GetList
  (-get-list [this]))

(declare ^:dynamic *get-list*)

(s/def ::post (s/merge ::post/post
                       (s/keys :req [::user/id ::user/full-name])))
(s/def ::posts (s/coll-of ::post))

(s/fdef get-list
  :args nil?
  :ret ::posts)

(defn get-list []
  (-get-list *get-list*))


(defprotocol GetById
  (-get-by-id [this id]))

(declare ^:dynamic *get-by-id*)

(s/fdef get-by-id
  :args (s/cat :id ::post/id)
  :ret (s/nilable ::post))

(defn get-by-id [id]
  (-get-by-id *get-by-id* id))
```

Напомню миграции создающие таблицы для постов и пользователей:

```sql
-- persistence/resources/db/migration/V2__create_post.sql

CREATE TABLE "post" (
  "id" bigint PRIMARY KEY,
  "title" varchar(255),
  "content" text,
  "created-at" timestamp
);
```

```sql
-- persistence/resources/db/migration/V3__create_user.sql

CREATE TABLE "user" (
  "id" bigint PRIMARY KEY,
  "login" varchar(255) UNIQUE,
  "full-name" varchar(255),
  "password-digest" text,
  "posts-ids" bigint[],
  "created-at" timestamp
);
```

Обратите внимание, что пользователь хранит идентификаторы постов с помощью postgresql
[массивов](https://postgrespro.ru/docs/postgrespro/10/arrays).
При этом добавляются
[операции](https://postgrespro.ru/docs/postgrespro/10/functions-array)
над массивами, например `@>` - "содержит".

Вот sql реализация запросов:

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

Отмечу, что БД не содержит индекса для `posts-ids`, но если вы будете хранить много данных, то
можете его [добавить](https://postgrespro.ru/docs/postgrespro/10/indexes-types).

Нам осталось использовать эти запросы и выполнить некоторые преобразования типов:

```clojure
(ns publicator.persistence.post-queries
  (:require
   [publicator.persistence.types]
   [hugsql.core :as hugsql]
   [hugsql.adapter.clojure-jdbc :as cj-adapter]
   [jdbc.core :as jdbc]
   [publicator.use-cases.abstractions.post-queries :as post-q]
   [publicator.domain.aggregates.post :as post]
   [publicator.domain.aggregates.user :as user]
   [clojure.set :as set]))

(hugsql/def-db-fns "publicator/persistence/post_queries.sql"
  {:adapter (cj-adapter/hugsql-adapter-clojure-jdbc)})

(defn- sql->post [row]
  (-> row
      (set/rename-keys {:user-id        ::user/id
                        :user-full-name ::user/full-name})
      (post/map->Post)))

(deftype GetList [data-source]
  post-q/GetList
  (-get-list [this]
    (with-open [conn (jdbc/connection data-source)]
      (map sql->post (post-get-list conn)))))

(deftype GetById [data-source]
  post-q/GetById
  (-get-by-id [this id]
    (with-open [conn (jdbc/connection data-source)]
      (when-let [row (post-get-by-id conn {:id id})]
        (sql->post row)))))

(defn binding-map [data-source]
  {#'post-q/*get-list*  (GetList. data-source)
   #'post-q/*get-by-id* (GetById. data-source)})
```

```clojure
(ns publicator.persistence.post-queries-test
  (:require
   [clojure.test :as t]
   [publicator.utils.test.instrument :as instrument]
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
  instrument/fixture
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
