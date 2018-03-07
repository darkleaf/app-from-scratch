# Практика

В качестве практики реализуем монаду either.

Рассмотрим программу на javascript. Это сценарий входа в систему.
Детали проверок опущены.

```javascript
function checkLoggedOut() {
  if false { return { type: "already-logged-in" } }
  return
}

function findUser(params) {
  if false { return { type: "authentication-failed" } }
  return { type: "user", id: 1}
}

function checkAuthentication(user, params) {
  if false { return { type: "authentication-failed" } }
  return
}

function checkParams(params) {
  if false { return { type: "invalid-params", explain: "some data" } }
  return
}

fucntion logIn(user) {
    return
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
(defn process [params]
  (or (check-logged-out)
      (check-params params)
      (let [user (find-user params)]
        (or (check-authentication user params)
            (do (user-session/log-in! user)
                {:type ::processed :user user})))))
```

Из-за того, что в `clojure` нет раннего возврата, сильно увеличивается вложенность.

Но есть способ лучше. Мы можем воспользоваться монадой either.
Появляется 2 обертки: `left` и `right`. Если в вычислении встречается значение `left`,
то вычисление прерывается и сразу возвращается это значение.

```clojure
(defn- check-logged-out= []
  (if true
    (right)
    (left {:type ::already-logged-in})))

(defn- find-user= [params]
  (if true
    (right {:type :user, :id 1})
    (left {:type ::authentication-failed})))

(defn- check-authentication= [user params]
  (if true
    (right)
    (left {:type ::authentication-failed})))

(defn- check-params= [params]
  (if true
    (right)
    (left {:type ::invalid-params})))

(defn- log-in! [user])

(defn process= [params]
  (let= [ok (check-logged-out=)
         ok (check-params= params)
         user (find-user= params)
         ok (check-authentication= user params)]
    (log-in! user)
    (return {:type ::processed :user user})))
```

`return` - это функция, синоним `right`.

Т.е. если `check-authentication=` вернет `(left {:type ::authentication-failed})`,
то и функция `process=` вернет то же самое.

Это напоминает железную дорогу.
Функцию `check-logged-out=` можно представить так:


<img src="/img/identity_state_value.jpg" alt="Identity, state, value">


<img src="/new/img/Recipe_RailwaySwitch.png" alt="railway fn">

А `let=` комбинирует подобные функции следующим образом:

<img src="/new/img/Recipe_RailwaySwitch2.png" alt="railway composition">

Из-за анологии с рельсами, наши функции, возвращающие `either` будут заканчиваться на `=`.

Подробности можно узнать из статьи
[Railway oriented programming](https://fsharpforfunandprofit.com/posts/recipe-part2/)

Таким образом, мы не используем ранний возврат и исключения.

## Соглашения именования

+ `f=` - функция, возвращающая `either`
+ `fs=` - коллекция функций, возвращающих `either`
+ `mv` - монадное значение, значение завернутое в `either`
+ `mf` - функция, завернутая в `either`

## Теория

`Either` - это множество из 2х элементов `Left` и `Right`. Причем и Left и Right - контейнеры,
т.е. содержат в себе значения других типов. В терминологии haskell `either` является:

+ бифунктором
+ аппликативным функтором
+ монадой
+ и много еще чем, но для нас неважным

Слова страшные и ими можно ругаться.

Это все нужно, чтобы терминология нашего кода соответствовала общепринятой.

Далее нам понадобится понимать сигнатуры функций haskell:

+ `Either a b` - тип `a` для `Left`, тип `b` для `Right`
+ `f :: a -> b -> c` - функция f 2х аргументов.
  Например, принимает строку, число и возвращает булев тип.
+ `g :: a -> a -> a` - функция 2х аргументов. Аргументы и возващаемое значение имеют одинаковый тип.
+ `h :: (a -> b) -> c` - функция, принимающая другую функцию.

*Бифунктор* - это некий контейнер, спрособный изменять свое содержимое с помощью функций.
Он определяет следующие функции:

+ `bimap :: (a -> b) -> (c -> d) -> Either a c -> Either b d`.
+ `first :: (a -> b) -> Either a c -> Either b c`
+ `second :: (b -> c) -> Either a b -> Either a c`

Если в `bimap` передаем `Left`, то к его значению применится первая функция, если `Rigth` - вторая.

Т.к`first` и `second` в clojure уже заняты, то назовем их `map-left` и `map-right` соответственно.

Вот тест для этих фукнкций:
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

*Аппликативный функтор* - контейнер, способный изменять свое значение с помощью функции, обернутой в контейнер этого же типа. Он определяет несколько функций, но нам важна только одна:

+ `(<*>) :: Either e (a -> b) -> Either e a -> Either e b`. Ее еще называют `fapply`.

+ Если передаем первым агрументом `Left`, то `fapply` вернет свой первый аргумент.
+ Если вторым аргументом передали `Left`, то `fapply` вернет второй аргумент.
+ Если оба аргумента `Right`, то распакованная функция применится к распакованному аргументу,
  а результат будет упакован в `Right`.

Вот тесты:

```clojure
(t/testing "<*>"
  (t/testing "right right"
    (let [mf  (right inc)
          mv  (right 0)
          ret (<*> mf mv)]
      (t/is (right? ret))
      (t/is (not= @mv @ret))))
  (t/testing "right left"
    (let [mf  (right inc)
          mv  (left :error)
          ret (<*> mf mv)]
      (t/is (left? ret))
      (t/is (= @ret :error))))
  (t/testing "left left"
    (let [mf  (left :f-error)
          mv  (left :v-error)
          ret (<*> mf mv)]
      (t/is (left? ret))
      (t/is (= @ret :f-error)))))
```

*Монада* - упрощенно контейнер, позволяющий создавать последованиельные цепочки вычислений.

+ `(>>=) :: Either e a -> (a -> Either e b) -> Either e b`. Иначе называется `bind`.
+ `(>>) :: Either e a -> Either e b -> Either e b`
+ `return :: a -> Either e a`

Надеюсь, вы уже поняли по типам, как работает `>>=`.
Первый агрумент - `Either` значение, второй - функция применяемая к содержимому первого аргумента и
возвращающая другой `Either`. Если первый агрумент `Left`, то он и будет возвращен.

`>>` частный случай `>>=`, когда нам не важен результат. Для `Either` он подобен оператору `or`.

```clojure
(t/testing ">>="
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

(t/testing ">>"
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
      (t/is (not (realized? spy))))))

(t/testing "return"
  (let [ret (return :val)]
    (t/is (right? ret))
    (t/is (= :val @ret))))
```

В haskell есть так называемая `do` нотация, фактически синтаксический сахар:

```haskell
do
  x <- Left "error"
  y <- Right 2
  return(x + y)
-- #> Left "error"

do
  x <- Right 1
  y <- Right 2
  return(x + y)
-- #> Right 3
```

Фактически тоже самое можно записать так:

```haskell
-- \x -> x - лямбда

Left "error" >>= (\x -> Right 2 >>= (\y -> return (x + y)))
-- #> Left "error"

Right 1 >>= (\x -> Right 2 >>= (\y -> return (x + y)))
-- #> Right 3
```

## Реализация

Clojure не Haskell. Haskell имеет мощную систему типов. Также он ленивый, т.е. не вычисляет
аргументы функции до ее вызова и не гарантирует порядок вычислений.
Поэтому в некоторых местах мы будем использовать макросы вместо функций,
по аналоги с макросом `or`, который вычисляет аргументы до первого истинного.

Возможно вы заметили, что у нашего `let=` и `do` нотации есть много общего. Сравните:

```clojure
(let= [x (right 1)
       y (right 2)]
   (return (+ x y)))
```

```haskell
do
  x <- Right 1
  y <- Right 2
  return(x + y)
```

В haskell do разворачивается в множество вызовов `>>=` с ананимными функциями, как это показано выше.
В отличие от haskell, для clojure, нет нужды обеспечивать порядок вычислений и реализовывать
поддержку прочих монад. К тому же создание множества анонимных функций и множественные вызовы `>>=`
существенно уменьшат производительность. Поэтому мы релизуем макрос `let=`, а с его помощью `<*>`, `>>=` и `>>`.

Пару слов про `let=`. Оригинальный `let` неявно заворачивает свое тело в `do`:

```clojure
(let [x 1]
  (do
    (prn x)
    x))
```

И это используется только для побочных эффектов.

Я не стал менять семантику для `let=`:

```clojure
(let= [x (right 1)]
  (prn x) ;; => напечает 1
  (return x))
```

Возможно вы заходите сделать так:

```clojure
(let= [x (right 1)]
  (some-fn=)
  (return x))
```

В этом случае результат `some-fn=` будет проигнорирован, даже если это будет `left`.

Явно используйте `>>`:

```clojure
(let= [x (right 1)]
  (>> (some-fn=)
      (return x)))
```

## Этапы выполнения

Я выделил 4 этапа:

+ 1, 2 - обязательные
+ 3 - желательный
+ 4 - маленький, необязательный.

На первом этапе нужно реализовать базовые функции по работе с `either`:

```clojure
(t/deftest step-1
  (t/testing "constructors and deref"
    (t/testing "with value"
      (let [val :val
            l   (left val)
            r   (right val)]
        (t/is (= :val @l @r))))
    (t/testing "without value"
      (let [l (left)
            r (right)]
        (t/is (= nil @l @r)))))
  (t/testing "print"
    (let [l (left)
          r (right)]
      (t/is (= "#<Left nil>" (pr-str l)))
      (t/is (= "#<Right nil>" (pr-str r)))))
  (t/testing "predicates"
    (t/testing "left?"
      (t/is (left? (left)))
      (t/is (not (left? (right)))))
    (t/testing "right?"
      (t/is (right? (right)))
      (t/is (not (right? (left)))))
    (t/testing "eihter?"
      (t/is (either? (left)))
      (t/is (either? (right)))
      (t/is (not (either? nil)))))
  (t/testing "invert"
    (let [l (invert (right :val))
          r (invert (left :val))]
      (t/is (and (left? l) (= :val @l)))
      (t/is (and (right? r) (= :val @r)))))
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
      (t/is (and (right? r) (= 1 @r))))))
```

На втором этапе нужно реализовать макрос `let=`:

```clojure
(t/deftest step-2
  (t/testing "return"
    (let [ret (return :val)]
      (t/is (right? ret))
      (t/is (= :val @ret))))
  (t/testing "let="
    (t/testing "right"
      (let [ret (let= [x (right 1)
                       y (right 2)]
                  (return (+ x y)))]
        (t/is (right? ret))
        (t/is (= 3 @ret))))
    (t/testing "left"
      (let [ret (let= [x (left 1)
                       y (right 2)]
                  (return (+ x y)))]
        (t/is (left? ret))
        (t/is (= 1 @ret))))
    (t/testing "computation"
      (t/testing "right"
        (let [effect-spy   (promise)
              side-effect! (fn [] (deliver effect-spy :ok))]
          (let= [x (right 1)
                 y (right 2)]
            (side-effect!)
            (return (+ x y)))
          (t/is (realized? effect-spy))))
      (t/testing "left"
        (let [y-spy        (promise)
              effect-spy   (promise)
              side-effect! (fn [] (deliver effect-spy :ok))]
          (let= [x (left 1)
                 y (right (do (deliver y-spy :ok) 2))]
            (side-effect!)
            (return (+ x y)))
          (t/is (not (realized? y-spy)))
          (t/is (not (realized? effect-spy))))))
    (t/testing "destructuring"
      (let [ret (let= [[x y] (right [1 2])]
                  (return (+ x y)))]
        (t/is (= 3 @ret))))
    (t/testing "asserts"
      (t/testing "bindings"
        (t/is (thrown? AssertionError
                       (let= [x 1]
                         (return x)))))
      (t/testing "result"
        (t/is (thrown? AssertionError
                       (let= [x (right 1)]
                         x)))))))
```

На третьем этапе реализовать `<*>`, `>>=`, `>>`:
```clojure
(t/deftest step-3
  (t/testing "<*>"
    (t/testing "right right"
      (let [mf  (right inc)
            mv  (right 0)
            ret (<*> mf mv)]
        (t/is (right? ret))
        (t/is (not= @mv @ret))))
    (t/testing "right left"
      (let [mf  (right inc)
            mv  (left :error)
            ret (<*> mf mv)]
        (t/is (left? ret))
        (t/is (= @ret :error))))
    (t/testing "left left"
      (let [mf  (left :f-error)
            mv  (left :v-error)
            ret (<*> mf mv)]
        (t/is (left? ret))
        (t/is (= @ret :f-error)))))
  (t/testing ">>="
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
  (t/testing ">>"
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
        (t/is (not (realized? spy)))))))
```

На четвертом бонустом этапе нужно реализовать аналог стандартного макроса `->`:
```clojure
(t/deftest step-4
  (t/testing "->="
    (t/testing "single arg"
      (let [ret (->= (right))]
        (t/is (right? ret))))
    (t/testing "right"
      (let [inc= (comp right inc)
            str= (comp right str)
            ret  (->= (right 0)
                      inc=
                      (str= 2 3))]
        (t/is (right? ret))
        (t/is (= "123" @ret))))
    (t/testing "right left"
      (let [fail= (fn [_] (left :error))
            ret   (->= (right 0)
                       fail=)]
        (t/is (left? ret))
        (t/is (= :error @ret))))
    (t/testing "left right"
      (let [inc= (comp right inc)
            ret   (->= (left :error)
                       inc=)]
        (t/is (left? ret))
        (t/is (= :error @ret))))))
```

## Задание

Начальный код и тесты находятся расположены по адресу https://repl.it/@darkleaf/blank-for-either
Сервис repl.it позволяет писать и выполнять код прямо в браузере.
Для этого вам нужно зарегистрироваться и форкнуть мой проект.

При выполнении внимательно смотрите на определения.
Внимательно смотрите на тесты.
Внимательно прочитайте шпаргалку.

Если что-то можно реализовать с помощью функции, то нужно написать функцию, а не макрос.

В следующем параграфе будет доступно мое решение для самостоятельной проверки.

## Шпаргалка

Все, что здесь перечислено точно вам пригодится.

***

https://clojure.org/api/cheatsheet - ваш главный справочный материал.
Ищите здесь информацию по любой стандартной функции/макросу.

***

Наверняка вам потребуется полимофизм. Для нашего случая Записи избыточны,
поэтому используйте Типы. Типы - это просто java классы,
они не реализуют никаких дополнительных протоколов и интерфейсов.

```clojure
(defprotocol P
  (m [this]))

(deftype T []
  P
  (m [this] :ok))
```

***

Используйте полиморфизм вместо ветвления(if, case).

***

Чтобы можно было использовать `@` нотацию, нужно реализовать интерфейс:

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

Делает это с помощью функции-обертки:

```clojure
(defprotocol P
  (m1 [this])
  (-m2 [this x y])

(defn m2 [x y this]
  (-m2 this x y))
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

Отлаживайте макросы:

```clojure
(-> '(let= [x (left 1)
            y (right 2)]
       (return (+ x y)))
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

***

Используйте:

+ [->](https://clojuredocs.org/clojure.core/-%3E)
+ [->>](https://clojuredocs.org/clojure.core/-%3E%3E)
+ [comp](https://clojuredocs.org/clojure.core/comp)
+ [identity](https://clojuredocs.org/clojure.core/identity)
+ [assert](https://clojuredocs.org/clojure.spec.alpha/assert)
+ [repeat](https://clojuredocs.org/clojure.core/repeat)
+ [interleave](https://clojuredocs.org/clojure.core/interleave)
