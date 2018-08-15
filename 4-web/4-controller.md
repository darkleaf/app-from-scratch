# Controller

Контроллер - адаптер для интерактора, который только конвертирует ring запрос
в данные, понятные интерактору. Также котнроллер содержит объявление маршрутов.
Формированием ring ответа занимается респондер, который мы рассмотрим в следующем параграфе.

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

Мы длолжны показать пользователю форму и обработать данные этой формы.

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

Контроллер должен только подготовить данные для интерактора, но не обрабатывать
его возвращаемое значение. Обратите внимание, что функции интерактора не вызываются,
вместо этого контроллер возвращает вектор из функции инерактора и его аргументов:

```clojure
[interactor/process id transit-params]
;; vs
(interactor/process id transit-params)
```

Это очень удобно для тестирования, т.к. не нужно подменять фукнкцию интерактора.
Вдобавок следующие стадиии обработки запроса получат не только результат интерактора,
но и аргументы, при которых он был получен.

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

Мы проверяем роутинг и то, что агрументы интерактора, полученные из запроса,
соответствуют спецификациям его агрументов.

Самостоятельно просмотрите все конроллеры. https://github.com/darkleaf/publicator/tree/master/web/src/publicator/web/controllers
