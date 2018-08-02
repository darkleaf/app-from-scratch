# Controller

Вот типичный контроллер нашего приложения c опущенными деталями:

```clojure
(ns publicator.web.controllers.post.update
  (:require
   [publicator.use-cases.interactors.post.update :as interactor]
   [publicator.web.controllers.base :as base]
   ;; ...
   ))

(defn form [{:keys [route-params]}]
  (let [id     (-> route-params :id Integer.)
        result (interactor/initial-params id)
        ctx    {:id id}]
    (base/handle ctx result)))

(defn handler [{:keys [transit-params route-params]}]
  (let [id     (-> route-params :id Integer.)
        result (interactor/process id transit-params)]
    (base/handle nil result)))

;;...

(def routes
  #{[:get "/posts/:id{\\d+}/edit" #'form :post.update/form]
    [:post "/posts/:id{\\d+}/edit" #'handler :post.update/handler]})
```

Контроллер содержит обработчики, соответствующие функциям интерактора.
Как и любой ring обработчик, он принимает http запрос и возвращает ответ.

Ключ `:route-params` добавляет библиотека роутинга, он содержит
параметры url, в нашем случае - `id`.

Форма отправляет данные в формате [transit](https://github.com/cognitect/transit-format).
Соответствующая middleware добавляет ключ `:transit-params` с данными формы.

Напомню спецификации функций интерактора:

```clojure
(s/fdef initial-params
  :args (s/cat :id ::post/id)
  :ret (s/or :ok  ::initial-params
             :err ::logged-out
             :err ::not-authorized
             :err ::not-found))

(s/fdef process
  :args (s/cat :id ::post/id
               :params any?)
  :ret (s/or :ok  ::processed
             :err ::logged-out
             :err ::not-authorized
             :err ::not-found
             :err ::invalid-params))
```

Наш контроллер должен обработать все эти случаи: 2 успешных и 4 провальных.
Реакция на случаи `::logged-out`, `::not-authorized` и `::not-found`
будет повторять реакцию для других интеракторов. По этом для обработки ответов
воспользуемся мультиметодом `base/handle`:

```clojure
(ns publicator.web.controllers.base
  (:require
   [publicator.web.template :as template]
   [publicator.web.form-renderer :as form-renderer]
   [publicator.web.transit :as transit]
   [ring.util.http-response :as http-response]))

(defmulti handle (fn [ctx resp] (first resp)))

(defmethod handle ::forbidden [_ _]
  {:status 403
   :headers {}
   :body "forbidden"})

(defmethod handle ::not-found [_ _]
  {:status 404
   :headers {}
   :body "not-found"})
```














notes:

про то, как отправлять post, put, delete через ссылки, формы

тест контроллера
