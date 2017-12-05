# Предметная область

Domain Driven Design(DDD) вводит понятия сущность(entity), объект-значение(object-value),
служба(service) и агрегат(aggregate).

Сущность - это нечно, что сохраняет идентичностью(identity) в процессе своего жизненного цикла.
Идентичность определяется идентификатором, а не своими атрибутами.
В нашем проекте есть только 2 сущности: пользователь и пост.

Объект-значение, напротив, полностью идентфицируется всеми своими атрибутами.
Например, это объект, представляющий цвет, деньги, составной идентификатор и т.п.

Служба - это некая операция без собственного состояния.
В нашем случае это:

+ создание пользователя
+ проверка пароля пользователя
+ создание поста
+ назначение атрибутов посту

Предметная область описывает те понятие, которые бизнес использует и без компьютерной автоматиазации.
Если бизенс связан с рознечной торговлей, то он и без компьютеров оперирует понятиями: товар, счет, деньги, продажа и т.п.
Это и есть сущности, объекты-значения и службы.

Агрегат - это совокупность взаимосвязанных объектов, которые мы
воспринимаем как единое целое с точки зрения изменения данных.
Они позоляют упорядочить и упростить связи между сущностями.
Каждый агрегат имеет корень и границу. Корневой объект - единственный
член агрегата, на который могут ссылаться объекты за границей агрегата.
А объеты, содежащиеся внутри границы, могут ссылаться друг на друга как угодно.
Корень агрегата должен иметь глобальную идентичность(уникальность),
а прочие члены уникальны только в границах агрегата.

В нашем случае всего 2 агрегата, состоящие только из одного корня - пользователь и пост.
Пост выделен в отдельный агрегат, т.к. в нашем приложении он является самостоятельной сущностью.

Дальше будут примеры кода на Clojure, если вы не знакомы с этим языком,
вот материалы, которые помогут разобраться:

+ [Reference](https://clojure.org/reference/documentation)
+ [Clojure for the Brave and True](https://www.braveclojure.com/)
+ [Cheatsheet](https://clojure.org/api/cheatsheet)


## Пользователь

Для моделирования сущностей в clojure лучше всего подходит [запись(record)](https://clojure.org/reference/datatypes).

```clojure
(ns publicator.domain.user)

(defrecord User [id login full-name password-digest])
```

Кроме всего прочего, агрегат должен поддерживать свою целостоность.
В clojure 1.9 появится функционал для описания спецификации данных - [clojure.spec](https://clojure.org/about/spec),
который отлично подходит для этой задачи. Чтобы валидировать агрегаты
вне зависимости от их типа воспользуемся
[протоколом(protocol)](https://clojure.org/reference/protocols):

```clojure
(ns publicator.domain.protocols.aggregate

(defprotocol Aggregate
  (spec [this] "Aggregate validation spec"))
```

```clojure
(ns publicator.domain.user
  (:require
   [publicator.domain.protocols.aggregate :as aggregate]
   [clojure.spec.alpha :as s]))

(s/def ::id ::id-generator/id)
(s/def ::login (s/and string? #(re-matches #"\w{3,255}" %)))
(s/def ::full-name (s/and string? #(re-matches #".{2,255}" %)))
(s/def ::password (s/and string? #(re-matches #".{8,255}" %)))
(s/def ::password-digest ::hasher/encrypted)

(s/def ::attrs (s/keys :req-un [::id ::login ::full-name ::password-digest]))

(defrecord User [id login full-name password-digest]
  aggregate/Aggregate
  (spec [_] ::attrs))
```

В дальнейшем я покажу, как валидитровать агрегат при каждом изменении.

Теперь объявим службы созднания пользователя и проверки пароля:

```clojure
(ns publicator.domain.user
  (:require
   [publicator.domain.abstractions.hasher :as hasher]
   [publicator.domain.abstractions.id-generator :as id-generator]))

(defrecord User [id login full-name password-digest])

(defn build [{:keys [login full-name password] :as params}]
  {:pre [(s/assert ::build-params params)]}
  (let [id              (id-generator/generate)
        password-digest (hasher/derive password)]
    (map->User {:id              id
                :login           login
                :full-name       full-name
                :password-digest password-digest})))

(defn authenticated? [user password]
  (hasher/check password (:password-digest user)))
```

В Clojure записи имеют только простые конструкторы `->User` и `map->User`, которые не содержат логики.
По этому конструктор, содержащий некую логику, это просто отдельная функция.

Чтобы создать пользователя нужно сгенерировать идентификатор и зашифовать пароль.
Хорошей идеей будет отложить выбор реализации и использовать абстракции.
Когда мы соберем больше информации о системе, мы добавим полнофункциональные реализации этих абстракций.

```clojure
(ns publicator.domain.abstractions.hasher
  (:refer-clojure :exclude [derive])
  (:require [clojure.spec.alpha :as s]))

(defprotocol Hasher
  (-derive [this password])
  (-check [this attempt encrypted]))

(declare ^:dynamic *hasher*)

(s/def ::encrypted string?)

(defn derive [password]
  {:post [(s/assert ::encrypted %)]}
  (-derive *hasher* password))

(defn check [attempt encrypted]
  {:pre [(s/assert ::encrypted encrypted)]}
  (-check *hasher* attempt encrypted))
```

```clojure
(ns publicator.domain.abstractions.id-generator
  (:require
   [clojure.spec.alpha :as s]))

(defprotocol IdGenerator
  (-generate [this]))

(declare ^:dynamic *id-generator*)

(s/def ::id some?)

(defn generate []
  {:post [(s/assert ::id %)]}
  (-generate *id-generator*))
```

[Проторолы](https://clojure.org/reference/protocols) в Clojure это аналог интерфейсов.
Функции, объявленные в протоколах имеют некоторые ограничения,
по этому обычно им добавляют префикс `-` и используют функции-обертки.
В в нашем случае обертки проверяют пред и постусловия.
Это больше похоже на абстрактый класс, а не на интерфейс.

Для установки значения используется [dynamic binding](https://clojure.org/reference/vars).

Для разработки и тестирования будем использовать поддельные объекты,
которые полностью реализуют протокол, но имеют наивную реализацию и не подходят для
production использования.

```clojure
(ns publicator.fake.id-generator
  (:require
   [publicator.domain.abstractions.id-generator :as id-generator]))

(deftype IdGenerator [counter]
  id-generator/IdGenerator

  (-generate [_]
    (swap! counter inc)))

(defn build []
  (IdGenerator. (atom 0)))

(defn binding-map []
  {#'id-generator/*id-generator* (build)})
```

```clojure
(ns publicator.fake.hasher
  (:require
   [publicator.domain.abstractions.hasher :as hasher]))

(deftype Hasher []
  hasher/Hasher

  (-derive [_ password]
    password)

  (-check [_ attempt encrypted]
    (= attempt encrypted)))

(defn binding-map []
  {#'hasher/*hasher* (->Hasher)})
```

Протоколы удобны еще и тем, что для некоторых тестов можно подготовить
специальный mock объект с помощью
[reify](https://clojuredocs.org/clojure.core/reify).

## Пост

Пост реализован аналогично модели пользователя:

```clojure
(ns publicator.domain.post
  (:require
   [publicator.domain.abstractions.id-generator :as id-generator]
   [publicator.domain.protocols.aggregate :as aggregate]
   [clojure.spec.alpha :as s]))

(s/def ::id ::id-generator/id)
(s/def ::title (s/and string? #(re-matches #".{1,255}" %)))
(s/def ::content string?)
(s/def ::author-id ::id-generator/id)

(s/def ::attrs (s/keys :req-un [::id ::author-id ::title ::content]))

(defrecord Post [id author-id title content]
  aggregate/Aggregate
  (spec [_] ::attrs))

(s/def ::build-params (s/keys :req-un [::title ::content ::author-id]))

(defn build [params]
  {:pre [(s/assert ::build-params params)]}
  (let [id (id-generator/generate)]
    (map->Post (merge params
                      {:id id}))))

(defn author? [post user]
  (= (:author-id post)
     (:id user)))
```

## Ассоциации(связи)

Наши модели - это простые объекты в памяти,
следовательно все ссылки между объектами однонапраленные.
Т.е. если A содержит ссылку на B, то нет простого
спрособа для B найти A.

DDD посвящает целый пораграф теме ассоциаций.
Важно сокращать количество связей, осталяя только значимые,
и сводить связи к однонаправленным.
Понятие агрегата было введено, в том числе,
чтобы уменьшить и упорядочить связи между сущностями.

В нашем простом случае пост содержит идентификатор автора.
Однако, это не единственно решение. Пользователь может
хранить массив идентификаторов созданных им постов.
Важно решить какое направление связи важнее.
Если бизнес-логика требует, чтобы пользователь знал
кол-во созданных постов, то выбор очевиден.
У этой схемы есть минус, т.к. несколько пользователей
могут оказаться авторами одного поста.

## Сслыки на примеры в проекте

+ [User](https://github.com/darkleaf/publicator/blob/master/src/publicator/domain/user.clj)
+ [Post](https://github.com/darkleaf/publicator/blob/master/src/publicator/domain/post.clj)
+ [Hasher](https://github.com/darkleaf/publicator/blob/master/src/publicator/domain/abstractions/hasher.clj)
+ [Id generator](https://github.com/darkleaf/publicator/blob/master/src/publicator/domain/abstractions/id_generator.clj)
+ [bindings для тестов](https://github.com/darkleaf/publicator/blob/master/test/publicator/fixtures.clj)
+ [fake.hasher](https://github.com/darkleaf/publicator/blob/master/src/publicator/fake/hasher.clj)
+ [fake.id-generator](https://github.com/darkleaf/publicator/blob/master/src/publicator/fake/id_generator.clj)

## Примечания

Генератор идентификаторов генерирует глобально уникальные идентификаторы.
Это может быть UUID, целое число. Стоит обратить внимание, что эти идентификаторы
не зависят от типа записи. Это сделано с целью упростить интерфес доступа к данными.
Этот подход имеет минус - придется просмотреть всю таблицы, хранящие корни агрегатов.
Если в вашем случае это не приемлемо - можно легко изменить интерфейс.

***

Вместо записей для моделирования можно использовать простые ассоциативные массивы:

```clojure
{:type :user
 :id 1
 :login "john"}
```

Записи реализуют интерфейс ассоциативных массивов, а также могут реализовывать протоколы.
В нашм случае записи и протоколы удобнее простых ассоциативных массивов.

***

Вместо установки реализации абстракции через dynamic binding можно было бы
явно передавать контекст первым аргументом во все функции или использовать монаду reader.
Явная передача контекста излишне многословна, а clojure предлагает стандарное решение для решения этой проблемы через механизм dynamic binding.

Отмечу, что dynamic binding подходит не для всех случаев.
В одном thread может быть только одно заначене. Фактически это singleton.
Для наших абстракций это подходит.

Установленные через dynamic binding значения видны только в текущем thread.
И если вы создаете новый тред, то в нем уже не будет прежних binging.
Однако многие clojure функкции и библиотеки сохраняют dynamic binding.
Функции `send`, `send-off`, `pmap`, `future` сохраняют контекст начиная с
[Clojure 1.3](https://github.com/clojure/clojure/blob/master/changes.md#234-binding-conveyance).

***

Функции могут проверять пред и постусловия.

```clojure
(defn foo [x]
  {:pre [(int? x)]
   :post [(string? %)]}
  (str "x: " x))
```

`defn` это макрос, который разворачиваетя следующим образом:

```clojure
(def foo
  (fn*
   ([x]
    (assert (int? x))
    (let [% (str "x: " x)]
      (assert (string? %)) %))))
```

В свою очередь `assert` это тоже макрос, и для production окружения можно отключить эти проверки.
