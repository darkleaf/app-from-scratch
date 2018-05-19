черновик


## Идентичность

Мы смоделировали состояние сущности,
но не саму сущность. Нам еще нужна идентичность.
Если забыли, то прочитайте [урок про управление состоянием](/1-clojure/3-state-management.md).

Для этого воспользуемся Refs(Ссылки).
Подробнее в [продолжении урока про состояние](/1-clojure/3.1-other.md).

```clojure
(let [alice (ref (build-person {:name "Alice"}))
      bob   (ref (build-person {:name "Bob"}))]
  (dosync
   (alter alice update :money - 10)
   (alter bob update :money + 10)))
```

Ссылки будут хранить не любые сущности, а только [агрегаты](/2-design/3-ddd.md) целиком.
Ведь все изменения вложенных сущностей должны проходить через корень.
Также агрегат должен проверять свою целостность, и в этом нам помогут валидаторы,
которые доступны для всех ссылочных типов(ref, atom, agent, var).

Агрегат будем моделировать соответствующим протоколом.
При этом каждый корень агрегата должен реализовать этот протокол:

```clojure
(ns publicator.domain.abstractions.aggregate
  (:require
   [clojure.spec.alpha :as s]))

(defprotocol Aggregate
  (id [this])
  (spec [this])
  (wrap-update [this]))

(s/def ::aggregate #(satisfies? Aggregate %))

(s/fdef wrap-update
        :ret ::aggregate)
```

```clojure
(defrecord Post [id title content created-at updated-at]
  aggregate/Aggregate
  (id [_] id)
  (spec [_] ::post)
  (wrap-update [this] (assoc this :updated-at (instant/now))))
```


Сделаем свой собственный ссылочный тип на основе Ref для хранения агрегатов.
При изменении агрегата он должен проверять, что класс агрегата не изменился,
не изменился и ее идентификатор, а также что агрегат после изменений остался валидным.
Кроме того мы должны вызывать `wrap-update` при изменении:

```
(ns publicator.domain.identity
  (:refer-clojure :exclude [alter])
  (:require
   [clojure.core :as core]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [clojure.spec.alpha :as s])
  (:import
   [clojure.lang Ref]))

(defn- build-validator [initial]
  (fn [new]
    (if (not= (class initial)
              (class new))
      (throw (ex-info "Aggregate class was changed."
                      {::type    :class-was-changed
                       ::initial initial
                       ::new     new})))
    (if (not= (aggregate/id initial)
              (aggregate/id new))
      (throw (ex-info "Aggregate id was changed."
                      {::type    :id-was-changed
                       ::initial initial
                       ::new     new})))
    (if-let [ed (s/explain-data (aggregate/spec new) new)]
      (throw (ex-info (str "Aggregate was invalid. "
                           (with-out-str (s/explain-out ed)))
                      {::type         :aggregate-was-invalid
                       ::explain-data ed})))
    true))

(s/def ::identity (s/and #(instance? Ref %)
                         #(-> % meta ::identity true?)))

(defn build [initial]
  (ref initial
       :meta {::identity true}
       :validator (build-validator initial)))

(s/fdef alter
        :args (s/cat :identity ::identity
                     :f ifn?
                     :args (s/* any?))
        :ret ::aggregate/aggregate)

(defn alter [identity f & args]
  (let [updater (comp aggregate/wrap-update f)]
    (apply core/alter identity updater args)))
```

```
(ns publicator.domain.identity-test
  (:require
   [publicator.domain.identity :as sut]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [publicator.utils.fixtures :as utils.fixtures]
   [clojure.spec.alpha :as s]
   [clojure.test :as t]))

(t/use-fixtures :once utils.fixtures/instrument)

(defrecord Aggregate [id property]
  aggregate/Aggregate
  (id [_] id)
  (spec [_] (fn [_] (some? property)))
  (wrap-update [this] (assoc this ::updated true)))

(defrecord OtherAggregate [id property]
  aggregate/Aggregate
  (id [_] id)
  (spec [_] (fn [_] (some? property)))
  (wrap-update [this] this))

(t/deftest identity-test
  (let [iagg (sut/build (->Aggregate 1 true))]
    (t/testing "spec"
      (t/is (s/valid? ::sut/identity iagg))
      (t/is (not (s/valid? ::sut/identity (ref nil)))))
    (t/testing "validator"
      (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Aggregate id was changed."
                              (dosync (sut/alter iagg assoc :id 2))))
      (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Aggregate class was changed."
                              (dosync (sut/alter iagg map->OtherAggregate))))
      (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Aggregate was invalid. "
                              (dosync (sut/alter iagg assoc :property nil)))))))

(t/deftest alter-test
  (let [iagg (sut/build (->Aggregate 1 true))]
    (t/is (-> @iagg ::updated nil?))
    (dosync (sut/alter iagg assoc :property false))
    (t/is (-> @iagg ::updated true?))))
```

Если бы все наши сущности хранились в одной структуре данных(world),
то можно было бы использовать чисто функциональный подход и
обходиться только состояниями сущностей:

```clojure
(-> (get-world)
    (update save-person (build-person {:name "Alice"}))
    (update save-person (build-person {:name "Bob"}))
    (update delete-last-person)
    (save-world)
```

Очевидно, что загружать все содержимое базы данных в память для любой операции это плохая идея
при больших объемах. Проект [Datascript](https://github.com/tonsky/datascript) использует
именно такой подход, но для маленьких объемов данных.
А [Datomic](https://www.datomic.com/) использует ленивую загрузку данных.



## Комментарии

Идентификаторы не зависят от типа сущности и уникальны в рамках всего проекта.

***

## Задание

Добавьте в проект https://github.com/darkleaf/publicator/tree/master/core агрегат Person.
