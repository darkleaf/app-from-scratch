# Queries

Абстракция Storage не позволяет делать произвольные выборки,
а выбирает весь агрегат целиком по его id.

Часто этого недостаточно.
Например, нужно найти пользователя по его логину, т.е. выбрать весь агрегат по условию.
Или выбрать таблицу постов с именами их авторов, т.е. выбрать агрегаты с дополнительными полями.
Или собрать аналитику и вернуть простые структуры данных.

Рассмотрим абстракцию запросов постов.
В этом неймспейсе описаны 2 запроса: Получить список постов и получить пост по id.

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

(s/fdef get-list
        :ret (s/coll-of ::post))

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

На самом деле тут выбирается не совсем пост. Этот неймспейс объявляет свой "тип" `::post`,
который кроме атрибутов поста содержит еще и атрибуты своего автора.
Это работает благодаря тому, что записи в clojure открыты к добавлению новых атрибутов.

В этом случае атрибуты поста не содержат неймспейсов, а атрибуты пользователя содержат.
Это нужно чтобы не было пересечения имен атрибутов.
Это видно по объявлению спецификаций. Пост использует `req-un`, а пост из запроса - `req`:

```clojure
(ns publicator.domain.aggregates.post
  ...)
(s/def ::post (s/keys :req-un [::id ::title ::content ::created-at]))

(ns publicator.use-cases.abstractions.post-queries
  (:require
   [publicator.domain.aggregates.post :as post]
   ...))
(s/def ::post (s/merge ::post/post
                       (s/keys :req [::user/id ::user/full-name])))
```

Теперь разберем фейковую реализацию.
В предыдущем параграфе я показывал, что данные хранятся в атоме, содержащем следующую структуру:

```clojure
{1 (->User 1 ...)
 2 (->Post 2 ...)
 3 (->Post 3 ...)}
```

Мы можем сделать так, чтобы фейк делал выборки из этого атома.
В нем нет индексов, кроме первичного ключа, и большинство выборок будут выполняться полным сканированием,
но это не критично для использования в тестах.

```clojure
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
```

Самостоятельно ознакомьтесь с выборками пользователей:

+ https://github.com/darkleaf/publicator/blob/master/core/src/publicator/use_cases/abstractions/user_queries.clj
+ https://github.com/darkleaf/publicator/blob/master/core/src/publicator/use_cases/test/fakes/user_queries.clj
