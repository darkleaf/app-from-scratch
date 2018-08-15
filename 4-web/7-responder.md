Рассмотрим контроллер обновления поста.
Он содержит 2 экшена: `form` и `handler`.
Первый показывает форму на основе начальных параметров из интерактора,
а второй обрабатывает данные от формы и передает их в интерактор.
Оба экшена используют мультиметод `base/handle` для обработки ответа от интерактора.

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

Экшен, как и любой ring обработчик, принимает http запрос и возвращает ответ.

Контроллер также содержит объявление роутинга для своих экшенов.

Ключ `:route-params` добавляет библиотека роутинга, он содержит
url параметры, в нашем случае - `id`.

Форма отправляет данные в формате [transit](https://github.com/cognitect/transit-format).
Соответствующая middleware добавляет ключ `:transit-params` с данными формы.


Наш контроллер должен обработать все эти случаи: 2 успешных и 4 провальных.
Реакция на случаи `::logged-out`, `::not-authorized` и `::not-found`
будет повторять реакцию для других интеракторов.

Для этого удобно использовать мультиметод, который будет выбирать реализацию
исходя из ответа интерактора. Также мультиметоды поддерживают наследование и для разных ответов
мы сможем объявить одну и туже реализацию.

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

Теперь, если интерактор вернет ответ с типом `::interactor/logged-out` или
`::interactor/not-authorized`,
то приложение  покажет сообщение `forbidden` со статусом 403.

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

В следующих параграфах мы подробно рассмотрим роутинг, рендеринг, презентеры, формы.

Контроллер не тестируется в изоляции, а тестирется сразу обработчик
всех запросов. Т.е. тестируются middleware, роутинг, контроллер, презентеры, шаблоны.
В подпроекте core интеракторы уже протестированы, и нет смысла повторять эти тесты.
Достаточно подменить интерактор заглушкой, возвращающей нужный ответ.
Большинство ответов интеракторов можно сгенерировать автоматически на основе спецификаций их функций.
Чтобы не повторять шаблонный код, воспользуемся макросом
[`do-template`](https://clojuredocs.org/clojure.template/do-template).
