## Command Query Separation

Самый простой способ работать с базой данных, не имея ОРМ - использовать Команды и Запросы.

```clojure
(ns publicator.interactors.abstractions.commands)


;; Т.к. при создании сущности еще до сохранения в базу она получает идентификатор,
;; то методы `insert` и `update` сливаются в `put`.
(defprotocol PutPost
  (-put-post [this post]))

(declare ^:dynamic *put-post*)

(defn put-post [post]
  (-put-post *put-post* post))
```

```clojure
(ns publicator.interactors.abstractions.queries)

(defprotocol GetUserById
  (-get-user-by-id [this id]))

(declare ^:dynamic *get-user-by-id*)

(defn get-user-by-id [id]
  (-get-user-by-id *get-user-by-id* id))
```

```clojure
(ns publicator.interactors.some-interactor
  (:require
   [publicator.domain.post :as post]
   [publicator.interactors.abstractions.commands :as commands]
   [publicator.interactors.abstractions.queries :as queries]))

...

(defn perform [params]
  ...
  (let [user (queries/get-user-by-id some-id)
        post (post/build params)
        post (assoc post :author-id (:id user))]
    (commands/put-post post)
    ...))
```

При желании можно добавить поддержку транзакций:

```clojure

(defprotocol Db
  (-wrap-tx [this body]))

(declare ^:dynamic *db*)

...

(defprotocol PutPost
  (-put-post [this tx post]))

(defmacro with-tx [tx-name & body]
  `(-wrap-tx *db* (fn [~tx-name] ~@body)))

...

(defprotocol GetUserById
  (-get-user-by-id [this tx id]))

...

(with-tx t
  (get-user-by-id t 123)
  (put-post t post)
  (put-post t another-post))
```

Это подходит для простых приложений. И требует дополнительного внимания от разработчика.

При таком подходе не отображаются идентификаторы сущностей на ссылки объектов в памяти.
В примере ниже в памяти программы мы одновременно оперируем одним и тем же
пользователем, но с разными состояниями. В итоге мы потеряем часть изменений:

```clojure
(let [user (queries/get-user-by-id 1)
      user (update user :achievements conj :fishing)
      ...
      author (gateway/get-user-by-id 1)
      author (update author :achievements conj :writing)]
  (commands/put-user user)
  ...
  (commands/put-user author))
```


Если мы не изменили агрегат, то все равно будет запрос к базе данных:

```clojure
(let [user (queries/get-user-by-id 1)]
  (command/put-user user))
```

В сложных сценариях вы можете забыть сделать сохранение.
Забыли сохранить пользователя:

```clojure
(let [user (queries/get-user-by-id 1)
      ...
      user (update user :achievements conj :writing)
      ...
      post (queries/get-user-by-id 2)
      post (assoc post :title "New title")]
  (gateway/put-post post))
```

Если приложение обрабатывает большой поток транзакций, то могут возникать dead-locks.
Придется вручную расставлять блокировки.
