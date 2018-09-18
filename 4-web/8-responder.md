# Респондер

Респондер отвечает за преобразование ответа интерактора к ring ответу.
Для этого он может использовать презентеры, шаблоны и формы,
эти детали мы рассмотрем в следующих параграфах.

Рассмотрим респондер для сценария обновления поста.
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

Как видно интерактор имеет 2 успешных и 4 провальных типа ответа.
Очевидно, реакция на случаи `::logged-out`, `::not-authorized` и `::not-found`
будет повторяться и для ответов других интеракторов.

Для обработки множества типов ответов удобно использовать мультиметод.
При этом мультиметоды поддерживают наследование, что позволяет
задать общие обработчики конкретным типам ответов.

```clojure
(ns publicator.web.responders.base
  (:require
   [publicator.web.responses :as responses]
   [publicator.web.presenters.explain-data :as explain-data]
   [publicator.web.routing :as routing]))

(defmulti result->resp first)

(defmethod result->resp ::forbidden [_]
  {:status 403
   :headers {}
   :body "forbidden"})

(defmethod result->resp ::not-found [_]
  {:status 404
   :headers {}
   :body "not-found"})

(defmethod result->resp ::invalid-params [[_ explain-data]]
  (-> explain-data
      explain-data/->errors
      responses/render-errors))

(defmethod result->resp ::redirect-to-root [_]
  (responses/redirect-for-form (routing/path-for :pages/root)))
```

Здесь мы объявляем мультиметод, принимающий 2 аргумента: ответ интерактора и вектор
аргументов. Также объявляются общие реализации для последующего связывания с конкретными ответами:

```clojure
(ns publicator.web.responders.post.update
  (:require
   [publicator.use-cases.interactors.post.update :as interactor]
   [publicator.web.responders.base :as responders.base]
   [publicator.web.responses :as responses]
   [publicator.web.forms.post.params :as form]))

(defmethod responders.base/result->resp ::interactor/initial-params [[_ post params]]
  (let [form (form/build-update (:id post) params)]
    (responses/render-form form)))

(derive ::interactor/processed ::responders.base/redirect-to-root)
(derive ::interactor/invalid-params ::responders.base/invalid-params)
(derive ::interactor/logged-out ::responders.base/forbidden)
(derive ::interactor/not-authorized ::responders.base/forbidden)
(derive ::interactor/not-found ::responders.base/not-found)
```

Отмечу, что ответ с типом `::interactor/initial-params` не содержит идентификатора поста,
этот идентификатор извлекается из аргументов с ктоторыми был вызван интерактор.

```clojure
(ns publicator.web.responders.post.update-test
  (:require
   [publicator.utils.test.instrument :as instrument]
   [publicator.web.responders.post.update :as sut]
   [publicator.web.responders.base :as responders.base]
   [publicator.use-cases.test.factories :as factories]
   [publicator.use-cases.interactors.post.update :as interactor]
   [publicator.web.responders.shared-testing :as shared-testing]
   [ring.util.http-predicates :as http-predicates]
   [clojure.spec.alpha :as s]
   [clojure.test :as t]))

(t/use-fixtures :once instrument/fixture)

(t/deftest all-implemented
  (shared-testing/all-responders-are-implemented `interactor/initial-params)
  (shared-testing/all-responders-are-implemented `interactor/process))

(t/deftest initial-params
  (let [result (factories/gen ::interactor/initial-params)
        resp   (responders.base/result->resp result)]
    (t/is (http-predicates/ok? resp))))
```

```clojure
(ns publicator.web.responders.shared-testing
  (:require
   [publicator.web.responders.base :as responders.base]
   [clojure.spec.alpha :as s]
   [clojure.test :as t]))

(defn all-responders-are-implemented [sym]
  (t/testing sym
    (let [[_ & pairs] (-> sym s/get-spec :ret s/describe)
          specs       (keep-indexed
                       (fn [idx item] (if (odd? idx) item))
                       pairs)
          implemented (-> responders.base/result->resp methods keys)]
      (doseq [spec specs]
        (t/testing spec
          (t/is (some #(isa? spec %) implemented) "not implemented"))))))
```
