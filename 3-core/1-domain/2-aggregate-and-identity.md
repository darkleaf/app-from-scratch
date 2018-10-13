# Агрегат

Ранее мы уже знакомились с [Агрегатами](/2-design/3-ddd.md).
Теперь поговорим об их реализации.

Возьмем сущности пост и комментарий.
В большинстве случаев они образуют агрегат,
где корнем будет пост, а внутренними сущностями будут комментарии.
Пост будет моделироваться Записью, а комментарии, например вектором хешей.
Если комментарии могут быть иерархическими, стоит воспользоваться специализированными
структурами вроде [datascript](https://github.com/tonsky/datascript).

Агрегат должен иметь идентификатор и проверять свою целостность.
Идентификатор может быть глобально уникальным либо уникальным
в рамках контекста. Этим контекстом может быть класс базовой сущности.
Для удобства будем использовать глобально уникальные идентификаторы.
Для проверки целостности будем использовать `clojure.spec`.

Смоделируем это с помощью протокола:

```clojure
(ns app.aggregate
  (:require
   [clojure.spec.alpha :as s]))

(defprotocol Aggregate
  (id [this])
  (spec [this]))

(s/def ::aggregate #(satisfies? Aggregate %))
```

Пост, как корень должен реализовать этот протокол:

```clojure
(ns app.post
  (:require
   [app.aggregate :as aggregate]
   [app.post.comment :as comment]
   [clojure.spec.alpha :as s]))

(s/def ::id pos-int?)
(s/def ::title string?)
(s/def ::content string?)
(s/def ::comments (s/coll-of ::comment/comment :kind vector?))
(s/def ::post (s/keys :req-un [::id ::title ::content ::comments]))

(defrecord Post [id title content comments]
  aggregate/Aggregate
  (id [_] id)
  (spec [_] ::post))
```

Комментарии моделируются простыми ассоциативными массивами.
Если будет нужно можно будет легко перейти на записи(record).

```clojure
(ns app.post.comment
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::content string?)
(s/def ::author string?)
(s/def ::comment (s/keys :req-un [::content ::author]))
```

Как вы уже заметили, комментарии не хранят идентификатор.
Комментарии хранятся в виде вектора, и идентификатором будет индекс комментария в этом векторе:

```clojure
;; map->Post генерируется при объявлении записи Post
(map->Post {:id       1
            :title    "Lorem ipsum"
            :content  "Some text"
            :comments [{:content "Awesome post!"
                        :author  "anonymous"}]})
```

# Identity

Мы смоделировали состояние агрегата.
Но нам еще нужна идентичность, чтобы работать с изменениями.

Есть 2 альтернативы: атомы и ссылки. Атомы используются для независимого изменения состояния,
а ссылки для скоординированного. Вряд ли приложение будет изменять одни и те же сущности
из нескольких потоков, однако важно правильно выразить намерение:

```clojure
;; версия с атомами
(swap! alice-acount dec 100)
(swap! bob-acount inc 90)
(swap! bank inc 10)

;; версия с ссылками
(dosync
 (alter alice-acount dec 100)
 (alter bob-acount inc 90)
 (alter bank inc 10))
```

Т.е. мы показываем, что эти изменения часть транзакции, а не сами по себе.
Таким образом мы будем использовать ссылки для моделирования идентичности.

Если забыли, то прочитайте уроки про управление состоянием:
[1](/1-clojure/3-state-management.md),
[2](/1-clojure/3.1-other.md).

Агрегат изменяется как одно целое, поэтому ссылка будет хранить весь агрегат целиком.

Ссылки могут иметь валидаторы. Воспользуемся ими, чтобы проверять изменения:

+ нельзя менять идентификатор корня агрегата
+ нельзя менять класс корня агрегата (может быть неактуально для некоторых приложений)
+ нельзя записывать невалидный агрегат

# Задание

В [исходниках](/3-core/1-domain/2-aggregates-and-identity) к этому уроку есть вышеперечисленные
листинги плюс неймспейс для идентичности и падающий тест валидатора.
Вам нужно реализовать валидатор.

Ознакомьтесь с:

+ https://clojuredocs.org/clojure.core/ref
+ https://clojuredocs.org/clojure.core/set-validator%21 либо используйте опцию `:validator`
+ https://clojuredocs.org/clojure.core/class
+ https://clojure.org/guides/spec
+ https://clojuredocs.org/clojure.core/ex-info
+ https://clojuredocs.org/clojure.core/ex-data

Проверьте себя:
+ https://github.com/darkleaf/publicator/blob/master/core/src/publicator/domain/identity.clj
