# Предметная область

Дальше будут примеры кода на Clojure, если вы не знакомы с этим языком,
вот материалы, которые помогут разобраться:

+ [Reference](https://clojure.org/reference/documentation)
+ [Cheatsheet](https://clojure.org/api/cheatsheet)
+ [Clojure for the Brave and True](https://www.braveclojure.com/)

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
Когда мы соберем больше информации о системе, мы добавим реализации этих абстракций,
а пока воспользуемся заглушками.

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

Для установки значения используется [dynamic binding](https://clojure.org/reference/vars).
Установленное значение `*hasher*` будет видно только в текущем thread.
Однако операции вроде `future` или `core.async/go` переносят тукущие биндинги в новый tread.

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

Пока не выбрана реализация будем пользоваться подделками.








## Обоснование

Почему рекорды, а не простые ассоциативные массивы?
