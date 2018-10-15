# Either

В дальнейшем нам потребуется моделировать вычисления, которые могут завершиться неудачей.
В мире функционального программирования для этого используют монаду Either.
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

function logIn(user) {
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

Т.е. функция `process` всегда возвращает или успешный ответ или ошибку.
Вызывающая сторона обработает результат и соответствующим образом сообщит пользователю системы.
В случае с web приложением, это может быть редирект или отображение ошибки.

Для обработки ошибок можно воспользоваться исключениями. Но:

+ мы всегда обрабатываем эти ошибки, т.е. это уже не исключительная ситуация
+ мы хотим передавать дополнительные данные, например ошибки валидации
+ исключения содержат stacktrace, и из-за его формирования снижается производительность

Вместо исключений в предыдущем примере используется ранний возврат из функции.
[Привет, Golang!](https://gobyexample.com/errors)
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
Вводится 2 типа-обертки: `Left` и `Right`. Если в вычислении встречается значение `Left`,
то вычисление прерывается и сразу возвращается это значение. Можно провести аналогию с железной дорогой.
Если в процессе встречается Left, то движение идет по красной ветке:

![railway composition](img/Recipe_RailwaySwitch2.png)

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
  (let= [ok   (check-logged-out=)
         ok   (check-params= params)
         user (find-user= params)
         ok   (check-authentication= user params)]
    (log-in! user)
    (right {:type ::processed :user user})))
```

Т.е. если `check-authentication=` вернет `(left {:type ::authentication-failed})`,
то и функция `process=` вернет то же самое.

Это напоминает железную дорогу.
Функцию `check-logged-out=` можно представить так:

![railway fn](img/Recipe_RailwaySwitch.png)

А `let=` комбинирует подобные функции следующим образом:

![railway composition](img/Recipe_RailwaySwitch2.png)

Из-за аналогии с рельсами, наши функции, возвращающие Either будут заканчиваться на `=`.

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

Есть 2 функции: `left` и `right`. Они принимают в качестве аргумента значение
и возвращают контейнер с этим значением. Они могут не принимать значение,
тогда в контейнере должен быть `nil`.

Т.к. clojure - динамический язык, удобно принять за `Right` любое значение кроме `Left`.

Доступ к значению в контейнере осуществляется с помощью
функции `extract`.

```clojure
(t/testing "with value"
      (let [val 42
            l   (sut/left val)
            r   (sut/right val)]
        (t/is (= val
                 (sut/extract l)
                 (sut/extract r)))))

(t/testing "without value"
  (let [l (sut/left)
        r (sut/right)]
    (t/is (= nil
             (sut/extract l)
             (sut/extract r)))))

(t/testing "default right"
  (t/is (sut/right? 1))
  (t/is (sut/right? "str"))
  (t/is (sut/right? []))
  (t/is (sut/right? nil)))
```

***

Есть предикаты: `(left? x)`, `(right? x)`.

```clojure
(t/testing "left?"
  (t/is (sut/left? (sut/left)))
  (t/is (not (sut/left? (sut/right)))))
(t/testing "right?"
  (t/is (sut/right? (sut/right)))
  (t/is (not (sut/right? (sut/left)))))
```

***

Полезно иметь функцию, которая меняет тип с `Left` на `Right` и наоборот:

```clojure
(t/testing "invert"
  (let [val 42]
    (t/is (= (sut/left val)
             (sut/invert (sut/right val))))
    (t/is (= (sut/right val)
             (sut/invert (sut/left val))))))
```

***

Для изменения содержимого контейнеров доступны функции:
+ `(bimap left-fn right-fn either)`
+ `(map-left left-fn either)`
+ `(map-right right-fn either)`

Если в `bimap` передаем `Left`, то к его значению применится первая функция, если `Rigth` - вторая.
`map-left` и `map-right` - частные случаи `bimap`.

```clojure
(t/testing "bimap"
  (t/is (= (sut/left 1)
           (->> 0 sut/left (sut/bimap inc identity))))
  (t/is (= (sut/right 1)
           (->> 0 sut/right (sut/bimap identity inc)))))

(t/testing "map-left"
  (t/is (= (sut/left 1)
           (->> 0 sut/left (sut/map-left inc))))
  (t/is (= (sut/right 0)
           (->> 0 sut/right (sut/map-left inc)))))

(t/testing "map-right"
  (t/is (= (sut/left 0)
           (->> 0 sut/left (sut/map-right inc))))
  (t/is (= (sut/right 1)
           (->> 0 sut/right (sut/map-right inc)))))
```

Напомню, что макрос [`->>`](https://clojuredocs.org/clojure.core/-%3E%3E)
преобразует `(->> 0 left (map-right inc))` в `(map-right inc (left 0))`.

***

Макрос `let=` позволяет использовать вместе выражения и прерывать исполнение,
если одно из них вернуло `Left`.

```clojure
(t/testing "right"
  (let [ret (sut/let= [x (sut/right 1)
                       y 2]
              (+ x y))]
    (t/is (= (sut/right 3)
             ret))))

(t/testing "left"
  (let [ret (sut/let= [x (sut/left 1)
                       y (sut/right 2)]
              (sut/right (+ x y)))]
    (t/is (= (sut/left 1)
             ret))))
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
      (sut/let= [x (sut/right 1)
                 y (sut/right 2)]
        (side-effect!)
        (sut/right (+ x y)))
      (t/is (realized? effect-spy))))

  (t/testing "left"
    (let [y-spy        (promise)
          effect-spy   (promise)
          side-effect! (fn [] (deliver effect-spy :ok))]
      (sut/let= [x (sut/left 1)
                 _ (deliver y-spy :ok)]
        (side-effect!))
      (t/is (not (realized? y-spy)))
      (t/is (not (realized? effect-spy))))))
```

Для проверки прерывания исполнения используются "шпионы". Шпион, это промис,
и мы можем проверить с помощью предиката `realized?` было ли доставлено ему какое-либо значение или нет.
Таким образом можно понять, вызывался ли тот или иной кусок кода.

Полезно иметь поддержку распаковки:

```clojure
(t/testing "destructuring"
  (let [ret (sut/let= [[x y] (sut/right [1 2])]
              (+ x y))]
    (t/is (= (sut/right 3)
             ret))))
```

***

Функция `>>=` позволяет строить цепочки следующего вида `(>>= either-value some-fn= another-fn=)`.
Т.е. ее первый аргумент - `Either`, а последующие - функции, принимающие обычные значения и
возвращающие `Either`. При этом если первый аргумент `Left` или любая функция вернула `Left`,
то выполнение прерывается.

```clojure
(t/testing "right rights"
  (let [mv   (sut/right 0)
        inc= (comp sut/right inc)
        str= (comp sut/right str)
        ret  (sut/>>= mv inc= str=)]
    (t/is (= (sut/right "1")
             ret))))

(t/testing "left right"
  (let [mv   (sut/left 0)
        inc= (comp sut/right inc)
        ret  (sut/>>= mv inc=)]
    (t/is (= (sut/left 0)
             ret))))

(t/testing "right lefts"
  (let [mv   (sut/right 0)
        fail= (fn [_] (sut/left :error))
        ret  (sut/>>= mv fail=)]
    (t/is (= (sut/left :error)
             ret)))))
```

***

Макрос `>>` тоже строит цепочки, но в отличие от `>>=` цепочки значений, а не функций.
Он полезен для последовательного вызова независимых функций. При этом, если в его аргументах
оказался `Left`, то он прерывает цепочку.

```clojure
(>> (check-attrs= attrs)
    (update-post= post attrs))
```

Если за `Left` принять `false`, а за Right - `true`, то `>>` будет подобен `and`,
т.е. будет вычислять выражения до первого ложного:

```clojure
(and
 (do (prn 1) true)
 (do (prn 2) false)
 (do (prn 3) true)) ;; 3 не будет напечатано
```

```clojure
(t/testing "rights"
  (let [ret (sut/>> (sut/right 1)
                    2)]
    (t/is (= (sut/right 2)
             ret))))
(t/testing "lefts"
  (let [spy (promise)
        ret (sut/>> (sut/left 1)
                    (deliver spy :ok))]
    (t/is (= (sut/left 1)
             ret))
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
станет последнее выражение внутри этой формы. Т.е. результат `(prn x)` игнорируется.

Не будем менять эту семантику для `let=`:

```clojure
(let= [x (right 1)]
  (prn x) ;; => напечатает 1
  (right x))
```

Возможно вы заходите сделать так:

```clojure
(let= [x (right 1)]
  (some-fn=)
  (right x))
```

В этом случае результат `some-fn=` будет проигнорирован, даже если это будет `Left`,
и результатом будет `(right 1)`.

Явно используйте `>>`:

```clojure
(let= [x (right 1)]
  (>> (some-fn=)
      (right x)))
```

## Задание

[Проект](/sources/1-clojure/7-either) содержит заготовку неймспейса `either.core` и
рассмотренные тесты.

Склонируйте этот репозиторий, запустите окружение и проверьте, что все тесты падают.

Задание разбито на 3 этапа:

1. реализация типов и функций над ними
2. реализация `let=`
3. реализация `>>=` и `>>`

При выполнении внимательно смотрите на тесты.
Прочитайте шпаргалку.
В конце этого параграфа будет ссылка для самостоятельной проверки.

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

Любой существующий тип, или все типы сразу можно расширить протоколом.
Однако `(= nil (class nil)`, т.е. `nil` не наследует от `Object`,
поэтому `nil` требует объявления отдельной реализации протокола.

```clojure
(extend-protocol Either
  Object
  (left? [this] false)
  (right? [this] true)

  nil
  (left? [this] false)
  (right? [this] true)
```

***

Т.к. Типы - по умолчанию не реализуют ничего, то вам нужно реализовать печать их экземпляров:

```clojure
(deftype T [val])

(defmethod print-method T [v ^java.io.Writer w]
  (doto w
    (.write "#<T ")
    (.write (pr-str (.val v)))
    (.write ">")))
```

***

При использовании протокола первый аргумент всегда - экземпляр класса, типа или записи.
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

Будет возвращена новая коллекция из тех же элементов.

***

Функции могут иметь различные определения в зависимости от количества аргументов:

```clojure
(defn foo
  ([] (foo nil))
  ([x] :some-body))
```

Если вы хотите сделать функцию с произвольным количеством аргументов, то
переменный вариант должен принимать столько же или больше аргументов:

```clojure
(defn foo
  ([x y] :do-something)
  ([x y & ys] :do-another))
```

***

Добавляйте в функцию поддержку переменного количества аргументов с помощью `cons` и `reduce`:

```clojure
(defn foo
  ([x y] :do-something)
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

Для cider используйте `M-x cider-macroexpand-1` или `C-c RET`.

***

Не забывайте о шаблонизации для макросов:

```clojure
(defmacro foo [x y]
  `(+ ~x ~y))

(defmacro bar [& body]
  `(do ~@body))
```

***

Если вам нужно объявить какой-то символ внутри макроса, используйте генератор символов:

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
(assert (-> bindings count even?))
```

***

Используйте те функции при работе с коллекциями, которые выражают ваши намерения:

```clojure

;; добавить элемент эффективным способом
(let [l (list 1 2 3)]
  (conj l 0)) ;; => (0 1 2 3)

(let [v [1 2 3]]
  (conj v 0)) ;; => [1 2 3 0]

;; добавить элемент в начало коллекции и получить последовательность
(let [l (list 1 2 3)]
  (cons 0 l)) ;; => (0 1 2 3)

(let [v [1 2 3]]
  (cons 0 v)) ;; => (0 1 2 3)
```

## Ответ

https://github.com/darkleaf/either

## Для любознательных

Для тех, кто знает Haskell, фактически мы реализуем вместо `Either` нечто вроде монадного трансформера
`EitherT a (IO b)`, т.к. функции в Clojure могут иметь побочные эффекты.

***

`bimap`, `>>=`, `>>` взяты из Haskell. Последние 2 адаптированы для использования с переменным
количеством аргументов.

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
