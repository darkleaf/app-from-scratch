# Ring

[Ring](https://github.com/ring-clojure/ring) - самая распространенная абстракция web сервера в clojure.
Вдохновлен ruby rack, python wsgi.

Обработчик запроса - простая функция, принимающая запрос, и возвращающая ответ.
И запрос и ответ - простые clojure структуры:

```clojure
(defn handler [req]
  {:status 200
   :headers {}
   :body (:uri req)})
```

В этом примере всегда отвечаем 200, а в теле ответа будет Uri.

Для добавления различного функционала мы можем использовать функции обертки -
middleware:

```clojure
(def app (-> handler
             (wrap-content-type "text/html")
             (wrap-keyword-params)
             (wrap-params)))
```

Middleware устроена следующим образом:

```clojure
(defn wrap-example [handler some-args]
  (fn [req]
    (let [req  (change-req req]
          resp (handler req)
          resp (change-resp resp)]
      resp)))
```

Т.е. middleware должна принять обработчик, некоторые параметры и вернуть новый обработчик.

Подробнее про структуру запросов и ответов, обработчики и middleware можно почитать
в [wiki проекта](https://github.com/ring-clojure/ring/wiki/Concepts).

## Server

Ring - абстракция над веб-сервером, а их может быть много, например:

+ [ring-jetty-adapter](https://github.com/ring-clojure/ring/tree/master/ring-jetty-adapter) -
  адаптер для java сервера [jetty](https://www.eclipse.org/jetty/)
+ [http-kit](http://www.http-kit.org/)
+ [aleph](http://aleph.io/aleph/http.html)

Мы будем использовать jetty.
Для этого нужно подключить 2 зависимости:

+ `ring/ring-core             {:mvn/version "1.6.2"}`
+ `ring/ring-jetty-adapter    {:mvn/version "1.6.2"}`

```clojure
(ns app.app
  (:require
   [clojure.pprint :as pp]
   [ring.adapter.jetty :as jetty]))

(defn handler [req]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (with-out-str (pp/pprint req))})

(jetty/run-jetty handler {:port 4445})
```

Этот пример запускает http сервер на 4445 порту.
В ответ на любой запрос тело ответа будет содержать красиво распечатанный запрос.

## Другие проекты

Есть и другие, несовместимые с ring, проекты:

+ [pedestal](http://pedestal.io/)
+ [catacumba](https://funcool.github.io/catacumba/latest/)
