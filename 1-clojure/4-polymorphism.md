# Полиморфизм

Полиморфизм дает возможность писать **один** код для работы с **многими** типами.
Полиморфизм можно грубо разделить на динамический и статический:

+ Динамический полиморфизм — это про абстрактные классы, интерфейсы, утиную типизацию,
  т.е. только в рантайме будет понятно, с каким типом будет работать наш код.
+ Статический полиморфизм — это в основном про шаблоны (genererics).
  Когда уже на этапе компиляции из одного шаблонного кода генерируется
  код специфичный для каждого используемого типа.

Здесь и далее я буду понимать под полимофизмом только динамический полиморфизм.

## Мультиметоды

```clojure
(defmulti foo identity)
;; identity - стандартная функция вида: (fn [x] x)

(defmethod foo :a [x]
  [:a x])

(defmethod foo :default [x]
  [:default x])

(assert (= [:a :a] (foo :a)))
(assert (= [:default :b] (foo :b)))
```

`defmulti` - объявляет мультиметод `foo` с функцией диспетчерезации `identity`.
Т.к. `identity` принимает один аргумент, то и наш метод будет также принимать один
аргумент. Фукция диспетчерезации на основе аргрументов вычисляет значение диспетчерезации, по
которому будет выбираться нужная реализация.

`defmethod` - объявляет реализацию для соответствующего значения диспетчерезации.
В нашем случае объявляется метод для `:a` и метод по умолчанию, который
будет обрабатывать оставшиеся случаи.

Рассмотрим пример посложнее.
Будем моделировать игру в камень-ножницы-бумага:

```clojure
(defmulti winner (fn [x y] (set [x y])))

(defmethod winner #{:rock}     [_ _] :drawn-game)
(defmethod winner #{:paper}    [_ _] :drawn-game)
(defmethod winner #{:scissors} [_ _] :drawn-game)

(defmethod winner #{:rock  :paper}    [_ _] :paper)
(defmethod winner #{:rock  :scissors} [_ _] :rock)
(defmethod winner #{:paper :scissors} [_ _] :scissors)

(assert (= :drawn-game (winner :rock :rock)))
(assert (= :paper (winner :rock :paper)))
(assert (= :paper (winner :paper :rock))) ;; симметричный случай
```

В clojure множества создаются функцией `set`, которая принимает коллекцию.
Чтобы объявить множество пользуются конструкцией: `#{1 2 3}` - множество из 1, 2 и 3.

`[_ _]` запись означает, что функция принимает 2 аргумента, но мы их не будем использовать.

Ключевой момент - функция диспетчерезации `(fn [x y] (set [x y]))`.
В данном случае заначение диспетчеризации - множество,
это обстоятельство позволяет не объявлять симметричные случаи, т.к. множество не поддерживает порядок.

Допустим, мы хотим добавить новый тип и получить игру камень-ножницы-бумага-пистолет.
Для этого нам не нужно модифицировать предыдущий код. Для этого нам нужно просто объявить
соответствующие методы для новых значений диспетчерезации:

```clojure
(defmethod winner #{:gun} [_ _] :drawn-game)
(defmethod winner #{:gun :rock}     [_ _] :gun)
(defmethod winner #{:gun :paper}    [_ _] :gun)
(defmethod winner #{:gun :scissors} [_ _] :gun)
```

Причем, таким образом можно расшять мультиметоды,
объявленные в другом пространстве имен или даже в другой библиотеке.

Мультиметоды поддерживают иерархии, которые позволяют реализовывать наследование,
в том числе множественное.

```clojure
;; keyword могут иметь пространство имен
;; ::a - краткое объявление кейворда в текущем пространстве имен
;; текущее пространство - user
(assert ::a :user/a)

(defmulti foo identity)
(defmethod foo ::a [_] "implementation for ::a")

(defmulti bar identity)
(defmethod bar ::b [_] "implementation for ::b")

;; множественное наследование
;; x - производная a и b
(derive ::x ::a)
(derive ::x ::b)

(assert (= "implementation for ::a" (foo ::x)))
(assert (= "implementation for ::b" (bar ::x)))
```

Наследование работает и в случае, если значение диспетчерезации - вектор:

```clojure
(defmulti foo (fn [x y] [x y]))
(defmethod foo [::a ::b] [_ _] true)

(derive ::x ::a)
(derive ::x ::b)

(assert (foo ::a ::b))
(assert (foo ::x ::x))
(assert (foo ::a ::x))
(assert (foo ::x ::b))
```

Т.к. мультиметоды использутю функцию диспетчерезации и поддерживают значение по умолчанию,
то это ООП, реализованное на принципах отправки сообщений (Alan Kay).

## Протоколы

В большинстве случаев достатоно диспетчерезации по классу первого аргумента:

```clojure
(defmulti foo (fn [this x y z] (class this)))
```

Для подобных случаев в clojure появились протоколы.
Но прежде, нужно познакомиться с записями:

```clojure
(defrecord User [id name])

(let [user (->User 1 "Alice")]
  (assert (= 1 (:id user)))
  (assert (= "Alice" (:name user)))
  (assert (= User (class user))))
```

Запись - это java класс, реализующий интерфейсы ассоциативных массивов(map).
Атрибуты записи реализованы как соответствующие поля java класса.
Кроме заранее указанных полей, запись может хранить произвольные:

```clojure
(let [user (->User 1 "Alice")
      user (assoc user :additional "some value")]
  (assert (= "some value" (:additional user)))
  (assert (= User (class user))))
```

Запись - это надстройка над Типом. Тип - простой java класс не реализующий каких-либо интерфейсов.
Как правило, пользуются Записями, а Типы используют, когда нужны "чистые" объекты.

```clojure

(deftype T [attr])

(.-attr (->T 1)) ;;=> 1
```

Допустим, кроме записи `User`, у нас есть еще запись `Admin` и
и мы хотим проверять может ли кто-то создавать пользователей:

```clojure
(defrecord User [id name])

(defrecord Admin [id name])

(defprotocol CreateUserAbility
  (can-create-user? [this]))

(extend User
  CreateUserAbility
  {:can-create-user? (fn [_] false)})

(extend Admin
  CreateUserAbility
  {:can-create-user? (fn [_] true)})

(let [user (->User 1 "Alice")]
  (assert (not (can-create-user? user))))

(let [admin (->Admin 1 "Bob")]
  (assert (can-create-user? admin)))
```

Протоколы могут содержать любое количество методов, как обычные java интерфесы,
но не поддерживают наследование. Протоколы могут расширять любую запись и любой java класс.

Кроме фукнции `extend` есть макросы `extend-type` и `extend-protocol`, которые делают запись
более удобной:

```clojure
(extend-type User
  CreateUserAbility
  (can-create-user? [_] false)
  OtherProtocol
  (some-method [_] :ok))

(extend-protocol CreateUserAbility
  User
  (can-create-user? [_] false)
  Admin
  (can-create-user? [_] true))
```

Если вы указываете реализацию протокола сразу при объявлении записи, то запись будет
реализовывать java интерфейс, что повысит производительность.
Эта же форма позволяет Записи реализовывать не протокол, а просто java интерфейс.

```clojure
(defprotocol CreateUserAbility
  (can-create-user? [this]))

(defrecord User [id name]
  CreateUserAbility
  (can-create-user? [_] false))

(defrecord Admin [id name]
  CreateUserAbility
  (can-create-user? [_] true))

(let [user (->User 1 "Alice")]
  (assert (not (can-create-user? user))))

(let [admin (->Admin 1 "Bob")]
  (assert (can-create-user? admin)))
```

Стоит отметить, что если вы хотите добавить метод для работы с конкретной записью,
то вам не нужен полиморфизм, и достаточно воспользоваться обычной функцией:

```clojure
(defrecord User [id name])

(defn present [this]
  (str (:id this) " - " (:name this)))

(let [user (->User 1 "Alice")]
  (assert (= "1 - Alice" (present user))))
```

Записи и протоколы не поддерживают наследование, но при расширении типа протоколом
можно воспользоваться функцией `extend` которая оперирует обычными
ассоциативными массивами и функциями. Таким образом может быть реализовано
наследование, примеси и т.п.:

```clojure
(defrecord A [])
(defrecord B [])

(defprotocol Proto
  (method [this]))

(let [impl {:method (fn [_] :some-body)}]
  (extend A Proto impl)
  (extend B Proto impl))

(assert (= (method (->A))
           (method (->B))))
```

Кроме всего этого, есть возможность создать анонимную реализацию протокола или java интерфейса
с помощью `reify`. Это удобно для тестирования или взаимодействия с java кодом.
Для этого `reify` поддерживает замыкания:

```clojure
(defprotocol Proto
  (method [this]))

(let [val      :val
      instance (reify Proto
                 (method [_] val))]
  (assert (= val (method instance))))
```

## Bechmark

Бенчмарк с помощью [criterium](https://github.com/hugoduncan/criterium).
Исходники в виде проекта можно получить [тут](sources/1-clojure/4-polymorphism/bench).

```clojure
(ns bench.bench
  (:require
   [criterium.core :as criterium]
   [clojure.template :as template]))

(defprotocol Proto
  (proto-method [this]))

(deftype A []
  Proto
  (proto-method [_] :ok))

(deftype B [])

(extend-type B
  Proto
  (proto-method [_] :ok))

(def c (reify
         Proto
         (proto-method [_] :ok)))

(deftype D [])
(defmulti multi-method class)
(defmethod multi-method D [_] :ok)

(defn bench []
  (template/do-template [method obj-expr]
                        (do
                          (prn '(method obj-expr))
                          (let [obj obj-expr]
                            (criterium/quick-bench (method obj)))
                          (print "\n\n\n"))
                        proto-method (->A)
                        proto-method (->B)
                        proto-method c
                        multi-method (->D)))
```


```
(proto-method (->A))
Evaluation count : 132892038 in 6 samples of 22148673 calls.
             Execution time mean : 2.863123 ns
    Execution time std-deviation : 0.019320 ns
   Execution time lower quantile : 2.838807 ns ( 2.5%)
   Execution time upper quantile : 2.879423 ns (97.5%)
                   Overhead used : 1.666364 ns



(proto-method (->B))
Evaluation count : 97196952 in 6 samples of 16199492 calls.
             Execution time mean : 4.596519 ns
    Execution time std-deviation : 0.064386 ns
   Execution time lower quantile : 4.548777 ns ( 2.5%)
   Execution time upper quantile : 4.701984 ns (97.5%)
                   Overhead used : 1.666364 ns

Found 1 outliers in 6 samples (16.6667 %)
    low-severe   1 (16.6667 %)
 Variance from outliers : 13.8889 % Variance is moderately inflated by outliers



(proto-method c)
Evaluation count : 131449896 in 6 samples of 21908316 calls.
             Execution time mean : 2.857191 ns
    Execution time std-deviation : 0.018021 ns
   Execution time lower quantile : 2.843163 ns ( 2.5%)
   Execution time upper quantile : 2.885828 ns (97.5%)
                   Overhead used : 1.666364 ns



(multi-method (->D))
Evaluation count : 15134856 in 6 samples of 2522476 calls.
             Execution time mean : 38.633649 ns
    Execution time std-deviation : 0.717109 ns
   Execution time lower quantile : 37.971764 ns ( 2.5%)
   Execution time upper quantile : 39.594540 ns (97.5%)
                   Overhead used : 1.666364 ns
```

Как видно, протоколы на порядок быстрее мультиметов. Реализация протокола в
`deftype`, `defrecord` или `reify` в 2 раза быстрее `extend`.

## Expression problem

Этот пункт необязателен, и для интересующихся я оставлю ссылку на
[соответствующую статью](https://www.ibm.com/developerworks/library/j-clojure-protocols)
