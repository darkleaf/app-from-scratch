# ООП
В этом параграфе я расскажу как clojure соотносится с ООП.

Есть 2 ассоцииации с ООП:

+ Инкапсуляция, наследование и полиморфизм
+ Обмен сообщениями

Clojure - полная противоположность привычным ООП языкам в смысле инкапсуляции.
В clojure функции и данные разделены. При этом поощряется максимальная открытость.
Как правило данные моделируются с помощью Map или записей, а они открыты для чтения
и модификации кем угодно.

А про полиморфизм и немного про наследование поговорим поподробнее.

## Полимрфизм

Полиморфизм дает возможность писать **один** код для работы с **многими** типами.
Полиморфизм можно грубо разделить на динамический и статический:

+ Динамический полиморфизм — это про абстрактные классы, интерфейсы, утиную типизацию,
  т.е. только в рантайме будет понятно, с каким типом будет работать наш код.
+ Статический полиморфизм — это в основном про шаблоны (genererics).
  Когда уже на этапе компиляции из одного шаблонного кода генерируется
  код специфичный для каждого используемого типа.

Здесь и далее я буду поинмать под полимофизмом только динамический полиморфизм.

Главной заслугой ООП языков, вроде C++ или Java, является реализация удобного и безопасного
динамического полиморфизма в сравнении с языком C, где приходилось использовать указатели на функции.

Полиморфизм дает возможность обращать зависимости и проектировать приложение с помощью плагинов.
Например, класс A использует класс B. `A --использует-> B`.
В этом случае зависимость между этими модулями в момент компиляции и в процессе исполнения совпадает.
А теперь реализуем плагин: класс A использует интерфейс IB, а В реализует IB.
`A --испльзует-> IB <-наследует-- B`. Таким образом в момент компиляции A знает только про интерфейс,
а в момент выполнения устанавливается конретная реализация IB - B.
Таким образом, без перекомпиляции пакета с A и IB можно изменять поведение A.
Это позволяет гибко подходить к разработке и откладывать приняние технических решений. В следующем разле
я раскажу подробно про этот подход.

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
Чтобы просто объявить множество пользуются конструкцией: `#{1 2 3}` - множество из 1, 2 и 3.

`[_ _]` запись означает, что функция принимает 2 аргумента, но мы их не будем использовать.

Ключевой момент - функция диспетчерезации.
`(fn [x y] (set [x y]))` - показывает, что наш мультиметод будет принимать 2 аргумента,
и возвращать множество. Это обстоятельство позволяет не объявлять симметричные случаи.

Допустим, мы хотим добавить новый тип и получить игру камень-ножницы-бумага-пистолет.
Для этого нам не нужно модифицировать предыдущий код. Для этого нам нужно просто объявить
соответствующие методы:

```clojure
(defmethod winner #{:gun} [_ _] :drawn-game)
(defmethod winner #{:gun :rock}     [_ _] :gun)
(defmethod winner #{:gun :paper}    [_ _] :gun)
(defmethod winner #{:gun :scissors} [_ _] :gun)
```

Причем, таким образом можно расшять мультиметоды,
объявленные в другом пространстве имен или даже в другой библиотеке.

Мультиметоды поддерживают иерархии, которые позволяют реализовывать наследование,
в том числе множественное. Мы не будем сейчас вдаваться в продробности.

```clojure
;; keyword могут иметь пространство имен
;; ::a - краткое объявление кейворда в текущем пространстве имен
;; текущее пространство - user
(assert ::a :user/a)

(defmulti foo identity)
(defmethod foo ::a [_] "implementation for ::a")

(defmulti bar identity)
(defmethod bar ::b [_] "implementation for ::b")

;; derive работает только с кейвордами, имеющими неймспейс
;; множественное наследование
;; x - производная a и b
(derive ::x ::a)
(derive ::x ::b)

(assert (= "implementation for ::a" (foo ::x)))
(assert (= "implementation for ::b" (bar ::x)))
```

`derive` работает только с кейвордами. При этом если значение диспетчерезации - вектор,
то наследование также работает:

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
то это напоминает ООП, реализованное на принципах отправки сообщений.

## Протоколы

В большинстве случаев достатоно диспетчерезации по классу первого аргумента:

```clojure
(defmulti foo (fn [this x y z] (class this)))
```

Именно для подобных случаев в clojure появились протоколы.
Но прежде, нужно познакомиться с записями:

```clojure
(defrecord User [id name])

(let [user (->User 1 "Alice")]
  (assert (= 1 (:id user)))
  (assert (= "Alice" (:name user)))
  (assert (= User (class user))))
```

Запись - это java класс, реализующая интерфейсы ассоциативных массивов(map).
Атрибуты записи реализованы как соответствующие поля java класса.
Кроме заранее указанных полей, запись может хранить произвольные:

```clojure
(let [user (->User 1 "Alice")
      user (assoc user :additional "some value")]
  (assert (= "some value" (:additional user)))
  (assert (= User (class user))))
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

Если вы указываете реализацию протокола сразу при объявлении записи, то запись будет
реализовывать java интерфейс, что существенно повысит производительность.

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

Бенчмарк с помощью [criterium](https://github.com/hugoduncan/criterium):

```clojure
(require '[criterium.core :as c])

(defprotocol Proto
  (method [this]))

(defrecord A [])

(extend A
  Proto
  {:method (fn [_] :ok)})

(defrecord B []
  Proto
  (method [_] :ok))

(do
  (let [a (->A)]
    (c/bench
     (method a)))

  (let [b (->B)]
    (c/bench
     (method b))))
```

```
Evaluation count : 3652721640 in 60 samples of 60878694 calls.
             Execution time mean : 9.284840 ns
    Execution time std-deviation : 0.664706 ns
   Execution time lower quantile : 8.490886 ns ( 2.5%)
   Execution time upper quantile : 10.647587 ns (97.5%)
                   Overhead used : 7.473709 ns

Found 1 outliers in 60 samples (1.6667 %)
    low-severe   1 (1.6667 %)
 Variance from outliers : 53.4630 % Variance is severely inflated by outliers

Evaluation count : 5103040980 in 60 samples of 85050683 calls.
             Execution time mean : 4.418015 ns
    Execution time std-deviation : 0.170834 ns
   Execution time lower quantile : 4.079148 ns ( 2.5%)
   Execution time upper quantile : 4.747486 ns (97.5%)
                   Overhead used : 7.473709 ns
```

Для A среднее время - 9.284840 ns, а для B - 4.418015 ns.
Т.е. вызов происходит в 2 раза быстрее.

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

Кроме всего этого, есть возможность создать анонимную реализацию протокола с помощью `reify`.
`reify` также поддерживает замыкания:

```clojure
(defprotocol Proto
  (method [this]))

(let [val      :val
      instance (reify Proto
                 (method [_] val))]
  (assert (= val (method instance))))
```

## Expression problem

Этот пункт необязателен, и для интересующихся я оставлю ссылку на
[соответствующую статью](https://www.ibm.com/developerworks/library/j-clojure-protocols)
