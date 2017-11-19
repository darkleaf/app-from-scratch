# Предметная область

Дальше будут примеры кода на Clojure, если вы не знакомы с этим языком,
вот материалы, которые помогут разобраться:

+ [Reference](https://clojure.org/reference/documentation)
+ [Cheatsheet](https://clojure.org/api/cheatsheet)
+ [Clojure for the Brave and True](https://www.braveclojure.com/)

## Пользователь

Для моделирования сущностей в clojure лучше всего подходят [записи(record)](https://clojure.org/reference/datatypes).

```clojure
(ns publicator.domain.user)

(defrecord User [id login full-name password-digest])
```

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

В Clojure записи имеют только простые конструкторы `->User` и `map->User`, которые не содержат логики. По этому конструктор с логикой - это просто отдельная функция.

Что бы создать пользователя нужно сгенерировать идентификатор и зашифовать пароль.
Хорошей идеей будет отложить выбор реализации и использовать абстракции.
Когда мы соберем больше информации о системе, мы добавим реализации этих абстракций.

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

[Проторолы](https://clojure.org/reference/protocols) это аналог интерфейсов.
Функции, объявленные в протоколах имеют некоторые ограничения,
по этому обычно им добавляют префикс `-` и используют функции-обертки.
В этому случае обертки проверяют пред и постусловия.

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

Реализация абстракции устанавливается следующим образом:

```clojure
(ns some-namespace
  (:require
   [publicator.fake.hasher :as fake.hasher]
   [publicator.fake.id-generator :as fake.id-generator]
   [publicator.domain.user :as user]))

(with-bindings (merge
                 (fake.hasher/binding-map)
                 (fake.id-generator/binding-map))
  (let [john (user/build {:login "john"
                          :full-name "John Doe"
                          :password "secret"})]
    (some-code john)))
```

## Сслыки на примеры в проекте

+ [User](https://github.com/darkleaf/publicator/blob/master/src/publicator/domain/user.clj)
+ [Post](https://github.com/darkleaf/publicator/blob/master/src/publicator/domain/post.clj)
+ [Hasher](https://github.com/darkleaf/publicator/blob/master/src/publicator/domain/abstractions/hasher.clj)
+ [Id generator](https://github.com/darkleaf/publicator/blob/master/src/publicator/domain/abstractions/id_generator.clj)
+ [bindings для тестов](https://github.com/darkleaf/publicator/blob/master/test/publicator/fixtures.clj)

Реализация Пользователя и Поста отличаются от описанного в этом разделе,
позже мы разберем эти отличия.

## Примечания

Здесь применяется принцип Dependency inversion из набора SOLID, который имеет следующую формулировку:

+ Модули верхних уровней не должны зависеть от модулей нижних уровней.
  Оба типа модулей должны зависеть от абстракций.
+ Абстракции не должны зависеть от деталей. Детали должны зависеть от абстракций.

Модули домена - это модули верхнего уровня. Id generator и Hasher это модули нижнего уровня.
Чем дальше от ввода/вывода модуль, тем выше его уровень абстракции.

***

Вместо записей для моделирования можно использовать простые ассоциативные массивы:

```clojure
{:type :user
 :id 1
 :login "john"}
```

Записи реализуют интерфейс ассоциативных массивов, а также могут реализовывать протоколы.
В нашм случае записи и протоколы удобнее простоых ассоциативных массивов.
В следующих разделах мы добавим Пользователю реализацию протокола.

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
Функции `send`, `send-off`, `pmap`, `future` сохраняют контекст начиная с [Clojure 1.3](https://github.com/clojure/clojure/blob/master/changes.md#234-binding-conveyance).

***

Функции могут проверять пред и постусловия.

```clojure
(defn foo [x]
  {:pre [(int? x)]
   :post [(string? %)]}
  (str "x: " x))
```

`defn` это макрос, корорый разворачиваетя таким образом:

```clojure
(def foo
  (fn*
   ([x]
    (assert (int? x))
    (let [% (str "x: " x)]
      (assert (string? %)) %))))
```

В свою очередь `assert` это тоже макрос, и для production окружения можно отключить проверки.
