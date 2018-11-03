# Безопасность

Приложение не использует готовые фреймворки и за безопасность полностью отвечает разработчик.

С web связаны различные уязвимости рассмотрим некоторые из них.

## Атака на идентификаторы

Мы используем последовательные идентификаторы, таким образом можно легко просмотреть все страницы.

Кроме этого идентификаторы глобально уникальны, и может возникнуть ситуация,
когда в интерактор удаления поста злоумышленник передал идентификатор пользователя,
а интерактор не проверяет права или тип агрегата.

Для защиты можно шифровать идентификаторы с помощью [hashids](https://hashids.org/) или
добавлять в query параметр подпись URL: `/posts/1?sign=some-sign`, а при обработке поверять эту подпись.

## Cross Site Request Forgery

На backend используется библиотека
[ring-anti-forgery](https://github.com/ring-clojure/ring-anti-forgery).
Она состоит из middleware, проверяющая CSRF токен, и динамической переменной, содержащей этот токен.


```clojure
(ns publicator.web.handler
  (:require
   [ring.middleware.anti-forgery :as ring.anti-forgery]
   [publicator.web.routing :as routing]
   ;;...
   ))

(defn build [binding-map]
  (-> routing/handler
      ;; ...
      ring.anti-forgery/wrap-anti-forgery
      ;; ...
      ))
```


Презентер получает токен, и добавляет его в ViewModel:

```clojure
(ns publicator.web.presenters.layout
  (:require
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.web.routing :as routing]
   [ring.middleware.anti-forgery :as anti-forgery]))

(defn present [req]
  (cond-> {:csrf anti-forgery/*anti-forgery-token*}
  ;; ...
  ))

```

В `html > head` добавляются мета-теги
```mustache
<meta name="csrf-token" content="{{csrf}}">
<meta name="csrf-param" content="__anti-forgery-token">
```

Мы используем [rails-ujs](https://github.com/rails/rails/tree/master/actionview/app/assets/javascripts)
для реализации ссылок, отправляющих post запросы.
Она автоматически подхватывает этот токен и вставляет его в форму при отправке.

[form-ujs](https://github.com/darkleaf/form-ujs) с помощью которой отправляются формы
так же подхватывает токен.
