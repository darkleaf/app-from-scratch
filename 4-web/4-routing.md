# Routing

Роутинг сопосталяет определенный запрос с обработчиком этого запроса.

Для Ring есть множество библиотек.
Из популярных можно отметить:

+ [compojure](https://github.com/weavejester/compojure)
+ [liberator](https://github.com/clojure-liberator/liberator)
+ [yada](https://github.com/juxt/yada)
+ [bidi](https://github.com/juxt/bidi)

Какую выберете вы, не очень принципиально. Однако, удобно, когда библиотека
поддерживает обратный роутинг, т.е. зная имя роута можно получить url запроса.
Иначе придется хардкодить url строками, и легко оставить битую ссылку.

Воспользуемся библиотекой [sibiro](https://github.com/aroemers/sibiro).
Это простая библиотека, не имеющая лишних для нашего приложения функций.

Определение маршрутов из рассмотренного ранее контроллера:

```clojure
(def routes
  #{[:get "/posts/:id{\\d+}/edit" #'form :post.update/form]
    [:post "/posts/:id{\\d+}/edit" #'handler :post.update/handler]})
```

Каждый маршрут моделируется вектором.
На последнем месте указывается название маршрута, благодаря этому работает обратный роутинг.
Маршруты объявляются как множество, т.е. маршруты не имеют порядка.
Нет вложенности, невозможно задать middeware для группы роутов,
например для `/admin`. Подробнее ознакомьтесь в [readme](https://github.com/aroemers/sibiro) библиотеки.

Напомню, что ранее мы обсуждали перезагрузку кода.
Если бы мы не использовали `#'form` и передавали просто значение `form`,
то при изменении кода пришлось бы перезагружать все приложение целиком.

Соберем воедино роуты всех контроллеров:

```clojure
(ns publicator.web.routing
  (:require
   [sibiro.core :as sibiro]
   [clojure.set :as set]
   [publicator.web.controllers.pages.root :as pages.root]
   [publicator.web.controllers.user.log-in :as user.log-in]
   [publicator.web.controllers.user.log-out :as user.log-out]
   [publicator.web.controllers.user.register :as user.register]
   [publicator.web.controllers.post.list :as post.list]
   [publicator.web.controllers.post.show :as post.show]
   [publicator.web.controllers.post.create :as post.create]
   [publicator.web.controllers.post.update :as post.update]
   [publicator.web.url-helpers :as url-helpers]))

(def routes
  (sibiro/compile-routes
   (set/union
    pages.root/routes
    user.log-in/routes
    user.log-out/routes
    user.register/routes
    post.list/routes
    post.show/routes
    post.create/routes
    post.update/routes)))

;; разберем через пару абзацев
(alter-var-root #'url-helpers/routes (constantly routes))
```

На основе скомпилированных роутов собираем обработчик приложения. Именно этот обработчик
будет передан вебсерверу.

```clojure
(ns publicator.web.handler
  (:require
   [sibiro.extras]
   [publicator.web.routing :as routing]
   ;; ...
   ))

;; ...

(defn build
  ([] (build {}))
  ([config]
   (-> routing/routes
       sibiro.extras/make-handler
       ;; ...
       ;; добавляем middleware
      )))
```

Также скомпилированные маршруты используются для обратного роутинга.

```clojure
(ns controller
 (:require
   [sibiro.core :as sibiro]
   [publicator.web.routing :as routing]
   ;;...
   ))

(defn handler [req]
  {:status 302, :headers {"Location" (sibiro/path-for routing/routes :root}}})

(def routes ...)
```

В другом контроллере объявлен маршрут с именем `:root`, и наш обработчик всегда будет
делать редирект на урл `:root`.

Однако появляется циклическая зависимость: `controller -> routing -> controller`,
что недопустимо в clojure. Есть несколько способов разорвать этот цикл, остановимся на
самом удобном. Возможно вы обратили внимание на сточку `(alter-var-root #'url-helpers/routes (constantly routes))` в `publicator.web.routing`. Именно тут и происходит разрыв цикла.

```clojure
(ns publicator.web.url-helpers
  (:require
   [sibiro.core :as sibiro]))

;; defined in publicator.web.routing
;; prevent cyclic dependecy
(declare routes)

(defn uri-for [& args]
  (apply sibiro/uri-for routes args))

(defn path-for [& args]
  (apply sibiro/path-for routes args))
```

И наш контроллер подключает `publicator.web.url-helpers`:

```clojure
(ns controller
 (:require
   [publicator.web.url-helpers :as url-helpers]
   ;;...
   ))

(defn handler [req]
  {:status 302, :headers {"Location" (url-helpers/path-for :root}}})

(def routes ...)
```
