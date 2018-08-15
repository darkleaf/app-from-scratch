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
    ;; ...
    ))

(defmulti ->resp (fn [result interactor-args] (first result)))

(defmethod ->resp ::forbidden [_ _]
  {:status 403
   :headers {}
   :body "forbidden"})

;; ...
```

Здесь мы объявляем мультиметод, принимающий 2 аргумента: ответ интерактора и вектор
аргументов. Также объявляются общие реализации для последующего связывания с конкретными ответами:

```clojure
(ns publicator.web.responders.post.update
  (:require
   [publicator.use-cases.interactors.post.update :as interactor]
   [publicator.web.responders.base :as base]
   [publicator.web.responders.responses :as responses]
   [publicator.web.forms.post.params :as form]
   [publicator.web.routing :as routing]))

(defmethod base/->resp ::interactor/initial-params [[_ params] [id]]
  (let [cfg  {:url    (routing/path-for :post.update/process {:id (str id)})
              :method :post}
        form (form/build cfg params)]
    (responses/render-form form)))

(derive ::interactor/processed ::base/redirect-to-root)
(derive ::interactor/invalid-params ::base/invalid-params)
(derive ::interactor/logged-out ::base/forbidden)
(derive ::interactor/not-authorized ::base/forbidden)
(derive ::interactor/not-found ::base/not-found)
```

Отмечу, что ответ с типом `::interactor/initial-params` не содержит идентификатора поста,
этот идентификатор извлекается из аргументов с ктоторыми был вызван интерактор.

```clojure
(ns publicator.web.responders.post.update-test
  (:require
   [publicator.utils.test.instrument :as instrument]
   [publicator.web.responders.post.update :as sut]
   [publicator.web.responders.base :as base]
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
        args   [1]
        resp   (base/->resp result args)]
    (t/is (http-predicates/ok? resp))))
```

```clojure
(ns publicator.web.responders.shared-testing
  (:require
   [publicator.web.responders.base :as base]
   [clojure.spec.alpha :as s]
   [clojure.test :as t]))

(defn all-responders-are-implemented [sym]
  (t/testing sym
    (let [[_ & pairs] (-> sym s/get-spec :ret s/describe)
          specs       (keep-indexed
                       (fn [idx item] (if (odd? idx) item))
                       pairs)
          implemented (-> base/->resp methods keys)]
      (doseq [spec specs]
        (t/testing spec
          (t/is (some #(isa? spec %) implemented) "not implemented"))))))
```
