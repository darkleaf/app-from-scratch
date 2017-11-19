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
