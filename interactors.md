# Interactors

Интерактор - это реализация сценария использования(usecase).
Термин пришел из Clean architecture Роберта Мартина.

Расмотрим сценарий входа в систему.
Его основной путь состоит из следующих шагов:

+ пользователь передает системе логин и пароль
+ система проверяет, что пользователь еще не вошел в систему
+ система проверяет, что логин и пароль имеют правильный тип и формат
+ система по логину находит пользователя и проверяет пароль
+ система записывает в сессию, что пользователь вошел в систему

Есть и побочные пути:

Показывать сообщение об ошибке, когда:

+ пользователь уже залогинен
+ логин и пароль имеют неправильный формат
+ в системе нет пользователя с таким логином или не подходит пароль

```clojure
(defn process [params]
  (let [err (or (check-logged-out)
                (check-params params))]
    (if (some? err)
      err
      (do
        (let [user (find-user params)
              err  (check-authentication user params)]
          (if (some? err)
            err
            (do
              (user-session/log-in! user)
              {:type ::processed :user user})))))))
```

Получается не очень понятно. Дело в том, что в clojure отсутствует ранний возврат из функции.
Но clojure это lisp, и есть макросы.
Существует библиотека [better-cond](https://github.com/Engelberg/better-cond),
которая линеаризует код:

```clojure
(b/defnc process [params]
  :let [err (or (check-logged-out)
                (check-params params))]
  (some? err) err
  :let [user (find-user params)
        err  (check-authentication user params)]
  (some? err) err
  :do (user-session/log-in! user)
  {:type ::processed :user user})
```

## Абстракции



## Сессия

В нашем случае сессия это нечно, что моделирует сессию работы пользователя с системой.
Фактически это key-value хранилище, сохраняющее состояние между разными запросами к системе.

```clojure
(ns publicator.interactors.abstractions.session
  (:refer-clojure :exclude [get set!]))

(defprotocol Session
  (-get [this k])
  (-set! [this k v]))

(declare ^:dynamic *session*)

(defn get [k]
  (-get *session* k))

(defn set! [k v]
  (-set! *session* k v))
```

Вот и протокол, подходящий под эти требования.

Для тестов нам подойдет наивная реализация:

```clojure
(ns publicator.fake.session
  (:require
   [publicator.interactors.abstractions.session :as session]))

(deftype FakeSession [storage]
  session/Session
  (-get [_ k] (get @storage k))
  (-set! [_ k v] (swap! storage assoc k v)))

(defn build []
  (FakeSession. (atom {})))

(defn binding-map []
  {#'session/*session* (build)})
```

Т.е. состояяние хранится внутри атома.
