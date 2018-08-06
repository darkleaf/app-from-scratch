# Controller

Контроллер в нашем приложении отвечает за обработку http запросов.
Они не содержат какой-либо бизнес-логики.
Т.е. это адаптер для интерактора, который конвертирует данные из
запроса в данные понятные интерактору и наоборот.

Контроллер - название довольно условное, и слабо соотносится с MVC.

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
        result (interactor/process id transit-params)
        ctx    {:id id}]
    (base/handle ctx result)))

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
будет повторять реакцию для других интеракторов. Поэтому для обработки ответов
воспользуемся мультиметодом `base/handle`:

```clojure
(ns publicator.web.controllers.base
  ;; ...
  )

(defmulti handle (fn [ctx resp] (first resp)))

(defmethod handle ::forbidden [_ _]
  {:status 403
   :headers {}
   :body "forbidden"})

(defmethod handle ::not-found [_ _]
  {:status 404
   :headers {}
   :body "not-found"})

;; ...
```

`handle` принимает 2 аргумента: контекст и ответ интерактора.
Ответ интерактора - вектор, первым элементом которого будет тип ответа.
Диспетчерезация идет как раз по типу ответа.

`handle` имеет 2 общих реализации: `::forbidden` и `::not-found`.
Теперь мы можем установить соответствие между типом ответа нашего интерактора
и общим обработчиком:

```clojure
(ns publicator.web.controllers.post.update
  (:require
   [publicator.use-cases.interactors.post.update :as interactor]
   [publicator.web.controllers.base :as base]
   ;; ...
   ))

;; ...

(derive ::interactor/logged-out ::base/forbidden)
(derive ::interactor/not-authorized ::base/forbidden)
(derive ::interactor/not-found ::base/not-found)

;; ...
```

Теперь, если интерактор вернет ответ с типом `::interactor/logged-out`,
то приложение  покажет сообщение `forbidden` с статусом 403.

Добавим методы для оставшихся типов:

```clojure
(ns publicator.web.controllers.post.update
  (:require
   [publicator.use-cases.interactors.post.update :as interactor]
   [publicator.web.presenters.explain-data :as explain-data]
   [publicator.web.forms.post.params :as form]
   [publicator.web.controllers.base :as base]
   [publicator.web.url-helpers :as url-helpers]))

(defn form [{:keys [route-params]}]
  (let [id     (-> route-params :id Integer.)
        result (interactor/initial-params id)
        ctx    {:id id}]
    (base/handle ctx result)))

(defn handler [{:keys [transit-params route-params]}]
  (let [id     (-> route-params :id Integer.)
        result (interactor/process id transit-params)
        ctx    {:id id}]
    (base/handle ctx result)))

(defmethod base/handle ::interactor/initial-params [ctx [_ params]]
  (let [cfg  {:url    (url-helpers/path-for :post.update/handler {:id (-> ctx :id str)})
              :method :post}
        form (form/build cfg params)]
    (base/render-form form)))

(defmethod base/handle ::interactor/processed [_ _]
  (base/redirect-form (url-helpers/path-for :pages/root)))

(defmethod base/handle ::interactor/invalid-params [_ [_ explain-data]]
  (-> explain-data
      explain-data/->errors
      base/errors))

(derive ::interactor/logged-out ::base/forbidden)
(derive ::interactor/not-authorized ::base/forbidden)
(derive ::interactor/not-found ::base/not-found)

(def routes
  #{[:get "/posts/:id{\\d+}/edit" #'form :post.update/form]
    [:post "/posts/:id{\\d+}/edit" #'handler :post.update/handler]})
```

В следующих параграфах мы подробно рассмотрим роутинг, формы, рендеринг.

Рассмотрим тестирование. Контроллер не тестируется в изоляции, а тестирется сразу обработчик
всех запросов. Т.е. тестируются middleware, роутинг, контроллер, презентеры, шаблоны.
В подпроекте core интеракторы уже протестированы, и нет смысла повторять эти тесты.
Достаточно подменить интерактор заглушкой, возвращающей нужный ответ.
Большинство ответов интеракторов можно сгенерировать автоматически на основе спецификаций их функций.
Чтобы не повторять шаблонный код, воспользуемся макросом
[`do-template`](https://clojuredocs.org/clojure.template/do-template).


```clojure
(ns publicator.web.requests.post.update-test
  (:require
   [clojure.test :as t]
   [publicator.utils.test.instrument :as instrument]
   [ring.util.http-predicates :as http-predicates]
   [ring.mock.request :as mock.request]
   [publicator.web.handler :as handler]
   [publicator.use-cases.interactors.post.update :as interactor]
   [publicator.use-cases.test.factories :as factories]
   [clojure.spec.alpha :as s]
   [clojure.template :as template]))

(t/use-fixtures :once instrument/fixture)

(t/deftest form
  (let [handler        (handler/build)
        req            (mock.request/request :get "/posts/1/edit")
        called?        (atom false)
        initial-params (fn [id]
                         (reset! called? true)
                         (t/is (= 1 id))
                         (factories/gen ::interactor/initial-params))
        resp           (binding [interactor/*initial-params* initial-params]
                         (handler req))]
    (t/is @called?)
    (t/is (http-predicates/ok? resp))))

(t/deftest handler
  (let [handler (handler/build)
        params  (factories/gen ::interactor/params)
        req     (-> (mock.request/request :post "/posts/1/edit")
                    (assoc :transit-params params))
        called? (atom false)
        process (fn [id p]
                  (reset! called? true)
                  (t/is (= 1 id))
                  (t/is (= params p))
                  (factories/gen ::interactor/processed))
        resp    (binding [interactor/*process* process]
                  (handler req))]
    (t/is @called?)
    (t/is (http-predicates/created? resp))))

(template/do-template
 [test-name interactor-resp predicate]

 (t/deftest test-name
   (let [handler        (handler/build)
         req            (mock.request/request :get "/posts/1/edit")
         initial-params (fn [_] interactor-resp)
         resp           (binding [interactor/*initial-params* initial-params]
                          (handler req))]
     (t/is (predicate resp))))

 form-logged-out
 (factories/gen ::interactor/logged-out)
 http-predicates/forbidden?

 form-not-authorized
 (factories/gen ::interactor/not-authorized)
 http-predicates/forbidden?

 form-not-found
 (factories/gen ::interactor/not-found)
 http-predicates/not-found?)

(template/do-template
 [test-name interactor-resp predicate]

 (t/deftest test-name
   (let [handler (handler/build)
         req     (mock.request/request :post "/posts/1/edit")
         process (fn [_ _] interactor-resp)
         resp    (binding [interactor/*process* process]
                   (handler req))]
     (t/is (predicate resp))))

 handler-logged-out
 (factories/gen ::interactor/logged-out)
 http-predicates/forbidden?

 handler-not-authorized
 (factories/gen ::interactor/not-authorized)
 http-predicates/forbidden?

 handler-not-found
 (factories/gen ::interactor/not-found)
 http-predicates/not-found?

 handler-invalid-params
 [::interactor/invalid-params (s/explain-data ::interactor/params {})]
 http-predicates/unprocessable-entity?)
```

Самостоятельно просмотрите остальные конроллеры. https://github.com/darkleaf/publicator/tree/master/web/src/publicator/web/controllers
