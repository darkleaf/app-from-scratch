# Практика

Предпологается, что вы прочитали дополнительные материалы и немного попрактиковались в написании
кода на clojure, например с помощью [Clojure Koans](https://github.com/functional-koans/clojure-koans).

В качестве практики реализуем монаду Either.

Не бойтесь слова "монада" и просто примите как данность, что Either это монада.
В конце будет материал для любознательных.

Рассмотрим программу на javascript. Это сценарий входа в систему.
Детали функций заменены заглушкой `realLogic()`.

```javascript
function checkLoggedOut() {
  if ( realLogic() ) { return { type: "already-logged-in" } }
  return
}

function findUser(params) {
  if ( realLogic() ) { return { type: "authentication-failed" } }
  return { type: "user", id: 1}
}

function checkAuthentication(user, params) {
  if ( realLogic() ) { return { type: "authentication-failed" } }
  return
}

function checkParams(params) {
  if ( realLogic() ) { return { type: "invalid-params", explain: "some data" } }
  return
}

fucntion logIn(user) {
    realLogic()
    right
}

function process(params) {
  var err

  err = checkLoggedOut()
  if err { return err }

  err = checkParams(params)
  if err { return err }

  let user = findUser(params)

  err = checkAuthentication(user, params)
  if err { return err }

  logIn(user)

  return { type: "processed", user: user }
}
```

Т.е. функция `process` всегда возвращает результат. Это может быть успех или ошибка.
Вызывающая сторона обработает результат и соответствующим образом сообщит пользователю системы.
В случае с web приложением, это может быть редирект или отображение ошибки.

Для обработки ошибок можно воспользоваться исключениями. Но:

+ мы всегда обрабатываем эти ошибки, т.е. это уже не исключительная ситуация
+ мы хотим передавать дополнительные данные, вроде объекта с ошибками валидации
+ исключения содержат stacktrace, из-за его генерации снижается производительность

Вместо исключений тут используется ранний возврат из функции. Привет, Golang!
Но теперь мы постоянно думаем об ошибках и это засоряет функцию.

Пример на `javascript` можно переписать на `clojure`:

```clojure
(defn check-logged-out []
  (if (real-logic)
    {:type ::already-logged-in}))

(defn find-user [params]
  (if (real-logic)
    {:type ::authentication-failed}
    {:type :user, :id 1}))

(defn check-authentication [user params]
  (if (real-logic)
    {:type ::authentication-failed}))

(defn check-params [params]
  (if (real-logic)
    {:type ::invalid-params, :explain "some-data"}))

(defn log-in! [user]
  (real-logic))

(defn process [params]
  (or (check-logged-out)
      (check-params params)
      (let [user (find-user params)]
        (or (check-authentication user params)
            (do (log-in! user)
                {:type ::processed :user user})))))
```

Здесь используется `or`, т.к. он вернет первый истинный результат, т.е. не `false` или не `nil`.
Функции `check-*` в случае ошибки вернут ассоциативный массив, который считается истинным.

Из-за того, что в `clojure` нет раннего возврата, сильно увеличивается вложенность.

Но есть способ лучше. Мы можем воспользоваться Either.
Появляется 2 обертки: `left` и `right`. Если в вычислении встречается значение Left,
то вычисление прерывается и сразу возвращается это значение. Можно провести аналогию с железной дорогой.
Если в процессе встречаетя Left, то движение идет по красной ветке:

<img src="/new/1-clojure/img/Recipe_RailwaySwitch2.png" alt="railway composition">

```clojure
(defn check-logged-out= []
  (if (real-logic)
    (left {:type ::already-logged-in})
    (right)))

(defn find-user= [params]
  (if (real-logic)
    (left {:type ::authentication-failed, :explain "some-data"})
    (right {:type :user, :id 1})))

(defn check-authentication= [user params]
  (if (real-logic)
    (left {:type ::authentication-failed})
    (right)))

(defn check-params= [params]
  (if (real-logic)
    (left {:type ::invalid-params})
    (right)))

(defn log-in! [user]
  (real-logic))

(defn process= [params]
  (let= [ok (check-logged-out=)
         ok (check-params= params)
         user (find-user= params)
         ok (check-authentication= user params)]
    (log-in! user)
    (right {:type ::processed :user user})))
```

Т.е. если `check-authentication=` вернет `(left {:type ::authentication-failed})`,
то и функция `process=` вернет то же самое.

Это напоминает железную дорогу.
Функцию `check-logged-out=` можно представить так:

<img src="/new/1-clojure/img/Recipe_RailwaySwitch.png" alt="railway fn">

А `let=` комбинирует подобные функции следующим образом:

<img src="/new/1-clojure/img/Recipe_RailwaySwitch2.png" alt="railway composition">

Из-за анологии с рельсами, наши функции, возвращающие Either будут заканчиваться на `=`.

Подробности можно узнать из статьи
[Railway oriented programming](https://fsharpforfunandprofit.com/posts/recipe-part2/)

Таким образом, мы не используем ранний возврат и исключения.

В случае с javascript можно провести аналогию с механизмом `Promise`.
Однако, он позволит строить только цепочки, в отличие от `let=`, который позволяет строить сложные
зависимости.

## Соглашения именования

+ `f=` - функция, возвращающая `either`
+ `fs=` - коллекция функций, возвращающих `either`
+ `mv` - значение, завернутое в `either`
+ `mf` - функция, завернутая в `either`


## Интерфейс

Есть 2 функции: `left` и `right`. Они принимают в качестве агрумента значение
и возвращают контейнер с этим значением. Они могут не принимать значение,
тогда в контейнере должен быть `nil`. Доступ к значению в контейнере определяется стандартным для
Clojure способом с помощью конструкции `@`.

```clojure
(t/testing "with value"
  (let [val :val
        l   (left val)
        r   (right val)]
    (t/is (= val @l @r))))

(t/testing "without value"
  (let [l (left)
        r (right)]
    (t/is (= nil @l @r)))))
```

***

Есть предикаты: `(left? x)`, `(right? x)`, `(either? x)`. Причем `either?` определен для всех типов,
а `left?` и `right?` только для наших контейнеров, т.е. `(left? 1)` бросит исключение.

```clojure
(t/testing "left?"
  (t/is (left? (left)))
  (t/is (not (left? (right)))))

(t/testing "right?"
  (t/is (right? (right)))
  (t/is (not (right? (left)))))

(t/testing "eihter?"
  (t/is (either? (left)))
  (t/is (either? (right)))
  (t/is (not (either? nil))))
```

***

Полезно иметь функцию, которая меняет обертку с Left на Right и наоборот:

```clojure
(t/testing "invert"
  (let [val :val
        l   (invert (right val))
        r   (invert (left val))]
    (t/is (and (left? l) (= val @l)))
    (t/is (and (right? r) (= val @r)))))
```

***

Для изменения содержимого контенеров доступны функции:
+ `(bimap left-fn right-fn either)`
+ `(map-left left-fn either)`
+ `(map-right right-fn either)`

Если в `bimap` передаем Left, то к его значению применится первая функция, если Rigth - вторая.
`map-left` и `map-right` - частные случаи `bimap`.

```clojure
(t/testing "bimap"
  (let [l (->> 0 left (bimap inc identity))
        r (->> 0 right (bimap identity inc))]
    (t/is (and (left? l) (= 1 @l)))
    (t/is (and (right? r) (= 1 @r)))))

(t/testing "map-left"
  (let [l (->> 0 left (map-left inc))
        r (->> 0 right (map-left inc))]
    (t/is (and (left? l) (= 1 @l)))
    (t/is (and (right? r) (= 0 @r)))))

(t/testing "map-right"
  (let [l (->> 0 left (map-right inc))
        r (->> 0 right (map-right inc))]
    (t/is (and (left? l) (= 0 @l)))
    (t/is (and (right? r) (= 1 @r)))))
```

Напомню, что макрос [`->>`](https://clojuredocs.org/clojure.core/-%3E%3E)
преобразует `(->> 0 left (map-right inc))` в `(map-right inc (left 0))`.

***

Макрос `let=` позволяет использовать вместе функции, возвращающие either и прерывать исполнение,
если одна из них вернула Left.

```clojure
(t/testing "right"
  (let [ret (let= [x (right 1)
                   y (right 2)]
              (right (+ x y)))]
    (t/is (right? ret))
    (t/is (= 3 @ret))))

(t/testing "left"
  (let [ret (let= [x (left 1)
                   y (right 2)]
              (right (+ x y)))]
    (t/is (left? ret))
    (t/is (= 1 @ret))))
```

Привязки `x` и `y` - соответствуют значениям контейнеров:

```clojure
(let= [x (right 1)
       y (right 2)]
  (prn x) ;; => 1
  (prn y) ;; => 2
  (right (+ x y)))
```

Проверка прерывания исполнения:

```clojure
(t/testing "computation"

  (t/testing "right"
    (let [effect-spy   (promise)
          side-effect! (fn [] (deliver effect-spy :ok))]
      (let= [x (right 1)
             y (right 2)]
        (side-effect!)
        (right (+ x y)))
      (t/is (realized? effect-spy))))

  (t/testing "left"
    (let [y-spy        (promise)
          effect-spy   (promise)
          side-effect! (fn [] (deliver effect-spy :ok))]
      (let= [x (left 1)
             y (right (do (deliver y-spy :ok) 2))]
        (side-effect!)
        (right (+ x y)))
      (t/is (not (realized? y-spy)))
      (t/is (not (realized? effect-spy))))))
```

Для проверки прерывания исполнения исполюзуются "шпионы". Шпион, это промис,
и мы можем проверить с помощью предиката `realized?` было ли доствлено ему какое-либо значение или нет.
Таким образом можно понять, вызывался ли тот или иной кусок кода.

Полезно иметь поддержку распаковки:

```clojure
(t/testing "destructuring"
  (let [ret (let= [[x y] (right [1 2])]
              (right (+ x y)))]
    (t/is (= 3 @ret))))
```

Все выражения внутни блока привязок должны быть either:

```clojure
(t/testing "bindings"
  (t/is (thrown? AssertionError
                 (let= [x 1]
                   (right x)))))
```

Последнее выражение в `let=` должно быть обернуто в either:

```clojure
(t/testing "result"
  (t/is (thrown? AssertionError
                (let= [x (right 1)]
                  x))))
```

***

Функция `>>=` позволяет строить цепочки следующего вида `(>>= either-value some-fn= another-fn=)`.
Т.е. ее первый агрумент или Left или Right, а последующие - функции, принимающие обычные значения и
возвращающие either. При этом если первый аргумент Left или любая функция вернула Left, то выполнение
прерывается.

```clojure
(t/testing "right rights"
  (let [mv   (right 0)
        inc= (comp right inc)
        str= (comp right str)
        ret  (>>= mv inc= str=)]
    (t/is (right? ret))
    (t/is (= "1" @ret))))

(t/testing "left right"
  (let [mv   (left 0)
        inc= (comp right inc)
        ret  (>>= mv inc=)]
    (t/is (left? ret))
    (t/is (= 0 @ret))))

(t/testing "right lefts"
  (let [mv   (right 0)
        fail= (fn [_] (left :error))
        ret  (>>= mv fail=)]
    (t/is (left? ret))
    (t/is (= :error @ret)))))
```

***

Макрос `>>` тоже строит цепочки, но в отличие от `>>=` цепочки значений, а не функций.
Он полезен для последовательного вызова независимых функций. При этом, если в его аргументах
оказался Left, то он прерывает цепочку.

```clojure
(>> (check-attrs= attrs)
    (update-post= post attrs))
```

Если за Left принять `false`, а за Right - `true`, то `>>` будет подобен `and`,
т.е. будет вычислять выражения до первого ложного:

```clojure
(and
 (do (prn 1) true)
 (do (prn 2) false)
 (do (prn 3) true)) ;; 3 не будет напечатано
```

```clojure
(t/testing "rights"
  (let [ret (>> (right 1)
                (right 2))]
    (t/is (right? ret))
    (t/is (= 2 @ret))))

(t/testing "lefts"
  (let [spy (promise)
        ret (>> (left 1)
                (right (do (deliver spy :ok)
                           2)))]
    (t/is (left? ret))
    (t/is (= 1 @ret))
    (t/is (not (realized? spy)))))
```

***

Оригинальный `let` неявно заворачивает свое тело в `do`:

```clojure
(let [x 1]
  (prn x)
  x)

(let [x 1]
  (do
    (prn x)
    x))
```

И это используется только для побочных эффектов, т.к. значением формы `(let ...)`
будет последнее выражение этой формы. Т.е. результат `(prn x)` игнорируется.

Я не стал менять эту семантику для `let=`:

```clojure
(let= [x (right 1)]
  (prn x) ;; => напечает 1
  (right x))
```

Возможно вы заходите сделать так:

```clojure
(let= [x (right 1)]
  (some-fn=)
  (right x))
```

В этом случае результат `some-fn=` будет проигнорирован, даже если это будет Left,
и результатом будет `(right 1)`.

Явно используйте `>>`:

```clojure
(let= [x (right 1)]
  (>> (some-fn=)
      (right x)))
```

## Задание

Начальный код и тесты находятся расположены по адресу https://repl.it/@darkleaf/blank-for-either
Сервис repl.it позволяет писать и выполнять код прямо в браузере.
Для этого вам нужно зарегистрироваться и форкнуть мой проект.

Задание разбито на 3 этапа:

1. реализация базового функционала
2. реализация `let=`
3. реализация `>>=` и `>>`

При выполнении внимательно смотрите на тесты.
Прочитайте шпаргалку.
В следующем параграфе будет доступно мое решение для самостоятельной проверки.

## Шпаргалка

https://clojure.org/api/cheatsheet - ваш главный справочный материал.
Ищите здесь информацию по любой стандартной функции/макросу.

***

Вам потребуются Типы. Типы - это просто java классы,
они не реализуют никаких дополнительных протоколов и интерфейсов.

```clojure
(deftype T [val])

(->T 1) ;; конструктор.
```

`->` - просто часть имени автоматически сгенерированной функции-конструктора.

***

Типы также как и Записи поддерживают протоколы:

```clojure
(defprotocol Proto
  (method [this]))

(deftype T []
  Proto
  (method [this] :ok))
```

***

Вместо ветвления (if, case) используйте полиморфизм.


```clojure
(defrecord Either [val kind])

(defn left? [either]
  (= (:kind either) :left))

(defn right? [either]
  (= (:kind either) :right))
```

```clojure
(defprotocol Either
  (left? [this])
  (right? [this]))

(deftype Left [val]
  Either
  (left? [_] true)
  (right? [_] false))

(deftype Right [val]
  Either
  (left? [_] false)
  (right? [_] true))
```

***

Чтобы можно было использовать `@` нотацию, нужно реализовать стандартный интерфейс:

```clojure
(deftype T [val]
  clojure.lang.IDeref
  (deref [_] val))

(let [x (->T 1)]
  @x) ;; => 1
```

***

Т.к. Типы - по умолчанию не реализуют ничего, то вам нужно реализовать печать их экземпляров:

```clojure
(deftype T [val]
  clojure.lang.IDeref
  (deref [_] val))

(defmethod print-method T [v ^java.io.Writer w]
  (doto w
    (.write "#<T ")
    (.write (pr-str @v))
    (.write ">")))
```

***

При использовании протокола первый аргумент всегда - экземляр класса, типа или записи.
Бывают ситуации, когда нужно поменять порядок аргументов.

Делайте это с помощью функции-обертки:

```clojure
(defprotocol P
  (m1 [this])
  (-m2 [this x y])

(defn m2 [x y this]
  (-m2 this x y))
```

***

Используйте паттерн Null-object. В частности функцию [identity](https://clojuredocs.org/clojure.core/identity).

Например:

```clojure
(map identity some-collection)
```

Коллекция не будет изменена.

***

Функции могут иметь различные определния в зависимости от количества аргуметов:

```clojure
(defn foo
  ([] (foo nil))
  ([x] :some-body))
```

Если вы хотите сделать функцию с произвольным количеством аргуметов, то
переменный вариант должен принимать столько же или больше аргументов:

```clojure
(defn foo
  ([x y] :do-somesing)
  ([x y & ys] :do-another))
```

***

Добавляйте в функцию поддержку переменного количества агруметов с помощью `cons` и `reduce`:

```clojure
(defn foo
  ([x y] :do-somesing)
  ([x y & ys] (reduce foo x (cons y ys))))
```

***

Иногда нужно использовать функции, которые еще не объявлены:

```clojure

(declare x)

(defn y []
  (x))

(defn x [] :ok)
```

***

Макросы могут быть рекурсивными.

***

Отлаживайте макросы:

```clojure
(-> '(let= [x (left 1)
            y (right 2)]
       (right (+ x y)))
    macroexpand-1
    clojure.pprint/pprint)
```

***

Полезно использовать шаблонизацию внутри макросов:

```clojure
(defmacro foo [x y]
  `(+ ~x ~y))

(defmacro bar [& body]
  `(do ~@body))
```

***

Если вам нужно объявить какой-то символ внутри макроса, используйте герератор символов:

```clojure
(defmacro foo [x y]
  `(let [z# 1]
     (+ ~x ~y z#)))


(let [z 3]
  (foo z z)) ;; => 7

;; (foo z z) преобразуется в
;; (let [z__14213__auto__ 1]
;;   (+ z z z__14213__auto__))
```

***

Бывают ситуации, когда такой способ не работает.
Например, вы вручную собираете форму:

```clojure
(defmacro foo [y]
  (let [val (gensym "val")]
    `(let [~val ~y]
       ~(list `+ val 2))))

(foo 1)

;; (let [val15558 1] (+ val15558 2))
```

***

Используйте утверждения:

```clojure
(assert (either? x))
```

***

Используйте те функции при работе с коллекциями, которые выражают ваши намерения:

```clojure

;; добавить элемент эффективным способом
(let [l (list 1 2 3)]
  (conj l 0)) ;; => (0 1 2 3)

(let [v [1 2 3]]
  (conj v 0)) ;; => [1 2 3 0]

;; добавить элемент в начало коллеекции и получить последовательность
(let [l (list 1 2 3)]
  (cons 0 l)) ;; => (0 1 2 3)

(let [v [1 2 3]]
  (cons 0 v)) ;; => (0 1 2 3)
```

## Для любознательных

Для тех, кто знает haskell, фактически мы реализуем вместо `Either` нечто вроде монадного трансформера
`EitherT a (IO b)`, т.к. функции в Clojure могут иметь побочные эффекты.

***

`bimap`, `>>=`, `>>` взяты из Haskell. Последние 2 адаптированы для использования с переменным
количеством агрументов.

***

Clojure не Haskell. Haskell имеет мощную систему типов. Также он ленивый, т.е. не вычисляет
аргументы функции до ее вызова и не гарантирует порядок вычислений.
Поэтому `>>` - макрос, а не функция, чтобы отложить вычисления.
Он подобен макросу `or`, который вычисляет аргументы до первого истинного.

***

В Haskell есть так называемая `do` нотация, фактически синтаксический сахар:

```haskell
do
  x <- Left "error"
  y <- Right 2
  right(x + y)
-- #> Left "error"

do
  x <- Right 1
  y <- Right 2
  right(x + y)
-- #> Right 3
```

Это эквивалентно:

```haskell
-- \x -> x - лямбда

Left "error" >>= (\x -> Right 2 >>= (\y -> right (x + y)))
-- #> Left "error"

Right 1 >>= (\x -> Right 2 >>= (\y -> right (x + y)))
-- #> Right 3
```

***

Возможно вы заметили, что у нашего `let=` и `do` нотации есть много общего. Сравните:

```clojure
(let= [x (right 1)
       y (right 2)]
   (right (+ x y)))
```

```haskell
do
  x <- Right 1
  y <- Right 2
  right(x + y)
```

***

В отличие от Haskell, для Clojure, нет нужды обеспечивать порядок вычислений и реализовывать
поддержку прочих монад. К тому же создание множества анонимных функций и множественные вызовы `>>=`
существенно уменьшат производительность. Поэтому `let=` реализован как макрос,
а с его помощью `>>=` и `>>`.
