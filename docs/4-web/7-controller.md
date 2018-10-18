# Controller

Контроллер - адаптер для интерактора, который конвертирует ring запрос
в данные, понятные интерактору. Также контроллер содержит объявление маршрутов.

Контроллер - название довольно условное, и в своем проекте вы можете использовать другое.

Рассмотрим контроллер для сценария обновления поста.
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

Мы должны показать пользователю форму и обработать данные этой формы.

Назовем экшены контроллера аналогично функциям интерактора: `initial-params` и `process`.

```clojure
(ns publicator.web.controllers.post.update
  (:require
   [publicator.use-cases.interactors.post.update :as interactor]))

(defn- req->id [req]
  (-> req
      :route-params
      :id
      Integer.))

(defn initial-params [req]
  (let [id (req->id req)]
    [interactor/initial-params id]))

(defn process [{:keys [transit-params] :as req}]
  (let [id (req->id req)]
    [interactor/process id transit-params]))

(def routes
  #{[:get "/posts/:id{\\d+}/edit" #'initial-params :post.update/initial-params]
    [:post "/posts/:id{\\d+}/edit" #'process :post.update/process]})
```

Ключ `:route-params` добавляет библиотека роутинга, он содержит
url параметры, в нашем случае - `id`.

Форма отправляет данные в формате [transit](https://github.com/cognitect/transit-format).
Соответствующая middleware добавляет ключ `:transit-params` с расшифрованными данными формы.

Тот факт, что контроллер не вызывает интерактор позволяет не подменять интерактор заглушкой
и не реализовывать повторно сценарии тестирования интерактора:

```clojure
(ns publicator.web.controllers.post.update-test
  (:require
   [publicator.utils.test.instrument :as instrument]
   [publicator.web.controllers.post.update :as sut]
   [publicator.use-cases.interactors.post.update :as interactor]
   [publicator.use-cases.test.factories :as factories]
   [ring.mock.request :as mock.request]
   [clojure.test :as t]
   [clojure.spec.alpha :as s]
   [sibiro.core]
   [sibiro.extras]))

(t/use-fixtures :once instrument/fixture)

(def handler
  (-> sut/routes
      sibiro.core/compile-routes
      sibiro.extras/make-handler))

(t/deftest initial-params
  (let [req             (mock.request/request :get "/posts/1/edit")
        [action & args] (handler req)
        args-spec       (-> `interactor/initial-params s/get-spec :args)]
    (t/is (= interactor/initial-params action))
    (t/is (nil? (s/explain-data args-spec args)))))

(t/deftest process
  (let [params          (factories/gen ::interactor/params)
        req             (-> (mock.request/request :post "/posts/1/edit")
                            (assoc :transit-params params))
        [action & args] (handler req)
        args-spec       (-> `interactor/process s/get-spec :args)]
    (t/is (= interactor/process action))
    (t/is (nil? (s/explain-data args-spec args)))))
```

Проверяем роутинг. Проверяем правильность возвращаемой функции интерактора,
а также соответствие полученных аргументов интерактора их спецификации.

Кроме контроллеров - адаптеров интеракторов, в веб приложении есть потребность в обычных страницах.
Экшены таких контроллеров возвращают не вектор, как описывалось ранее, а обычный ring ответ:

```clojure
(ns publicator.web.controllers.pages.root
  (:require
   [publicator.web.responses :as responses]))

(defn show [_]
  (responses/render-page "pages/root" {}))

(def routes
  #{[:get "/" #'show :pages/root]})
```

В предыдущем параграфе мы видели middleware, оборачивающую экшены контроллеров:

```
(defn middleware [handler]
  (fn [req]
    (let [[interactor & args] (handler req)
          result              (apply interactor args)]
      (responder result args))))
```

Для того, чтобы обрабатывать обычные ring ответы добавим соответствующее условие:

```clojure
(ns publicator.web.middlewares.responder
  (:require
   [publicator.web.responders.base :as responders.base]
   ;; ..
   ))

(defn wrap-reponder [handler]
  (fn [req]
    (let [resp (handler req)]
      (if (vector? resp)
        (let [[interactor & args] resp
              result              (apply interactor args)]
          (responders.base/result->resp result))
        resp))))
```

Самостоятельно просмотрите
[все контролеры](https://github.com/darkleaf/publicator/tree/master/web/src/publicator/web/controllers).
