# Storage

Мы уже познакомились
с [способами работы с БД](/2-design/6-persistence)
и разобрались [как моделировать идентичности](/3-core/1-domain/2-aggregate-and-identity).
Теперь рассмотрим основную абстракцию хранилища подробнее.

Абстрактное хранилище для нашего приложения должно удовлетворять следующим требованиям:

+ поддержка транзакций
+ получение идентичности по идентификатору
+ реализация [Identity Map](https://martinfowler.com/eaaCatalog/identityMap.html)
+ создание идентичности из ее состояния

Не во всех приложениях безвозвратное удаление имеет смысл.
Чвсто удаление заменяют архивированием.
Опустим этот функционал.

Выборки по различным условиям рассмотрим в следующем параграфе.

Основной код абстракции:

```clojure
(ns publicator.use-cases.abstractions.storage
  (:require
   [clojure.spec.alpha :as s]
   [publicator.domain.abstractions.id-generator :as id-generator]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [publicator.domain.identity :as identity]
   [publicator.utils.ext :as ext]))

(defprotocol Storage
  (-wrap-tx [this body]))

(defprotocol Transaction
  (-get-many [t ids])
  (-create [t state]))

(s/fdef get-many
  :args (s/cat :tx any?
               :ids (s/coll-of ::id-generator/id :distinct true))
  :ret (s/map-of ::id-generator/id ::identity/identity))

(s/fdef create
  :args (s/cat :tx any?
               :state ::aggregate/aggregate)
  :ret ::identity/identity)

(defn get-many [t ids] (-get-many t ids))
(defn create   [t state] (-create t state))

(declare ^:dynamic *storage*)

(defmacro with-tx
  "Note that body forms may be called multiple times,
   and thus should be free of side effects."
  [tx-name & body-forms-free-of-side-effects]
  `(-wrap-tx *storage*
            (fn [~tx-name]
              ~@body-forms-free-of-side-effects)))

```

Можно попытаться написать спецификацию для `get-many`, которая будет проверять поддержку Identity Map,
но эта спецификация будет очень сложной, поэтому проверка ложится на программиста и тесты.

С помощью макроса `with-tx` мы можем удобно объявлять транзакцию:

```clojure
(storage/with-tx t
  (storage/create t user-1-state)
  (storage/create t user-2-state))
```

Для оптимизации запросов, протокол транзакции поддерживает только метод `get-many`,
а метод `get-one` выражается через него :

```clojure
(s/fdef get-one
  :args (s/cat :tx any?
               :id ::id-generator/id)
  :ret (s/nilable ::identity/identity))

(defn get-one [t id]
  (let [res (get-many t [id])]
    (get res id)))
```

Часто мы будем выполнять только одно действие в транзакции, для этого
объявим вспомогательные методы:

```clojure
(s/fdef tx-get-one
  :args (s/cat :id ::id-generator/id)
  :ret (s/nilable ::aggregate/aggregate))

(defn tx-get-one [id]
  (with-tx t
    (when-let [x (get-one t id)]
      @x)))


(s/fdef tx-get-many
  :args (s/cat :ids (s/coll-of ::id-generator/id :distinct true))
  :ret (s/map-of ::id-generator/id ::aggregate/aggregate))

(defn tx-get-many [ids]
  (with-tx t
    (->> ids
         (get-many t)
         (ext/map-vals deref))))


(s/fdef tx-create
  :args (s/cat :state ::aggregate/aggregate)
  :ret ::aggregate/aggregate
  :fn #(= (-> % :args :state)
          (-> % :ret)))

(defn tx-create [state]
  (with-tx t
    @(create t state)))


(s/fdef tx-alter
  :args (s/cat :state ::aggregate/aggregate
               :f fn?
               :args (s/* any?))
  :ret (s/nilable ::aggregate/aggregate))

(defn tx-alter [state f & args]
  (with-tx t
    (when-let [x (get-one t (aggregate/id state))]
      (dosync
       (apply alter x f args)))))
```

Что бы лучше понять, как это использовать, разберитесь в тестах фейковой реализации этой абстракции:

```clojure
(ns publicator.use-cases.test.fakes.storage-test
  (:require
   [publicator.use-cases.test.fakes.storage :as sut]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [publicator.domain.identity :as identity]
   [publicator.utils.test.instrument :as instrument]
   [clojure.test :as t]))

(t/use-fixtures :once instrument/fixture)

(t/use-fixtures
  :each
  (fn [f]
    (with-bindings (sut/binding-map (sut/build-db))
      (f))))

(defrecord Test [counter]
  aggregate/Aggregate
  (id [_] 42)
  (spec [_] any?))

(t/deftest create
  (let [test (storage/tx-create (->Test 0))
        id (aggregate/id test)]
    (t/is (some? test))
    (t/is (some? (storage/tx-get-one id)))))

(t/deftest change
  (let [test (storage/tx-create (->Test 0))
        id   (aggregate/id test)
        _    (storage/tx-alter test update :counter inc)
        test (storage/tx-get-one id)]
    (t/is (= 1 (:counter test)))))

(t/deftest identity-map-persisted
  (let [test (storage/tx-create (->Test 0))
        id   (aggregate/id test)]
    (storage/with-tx t
      (let [x (storage/get-one t id)
            y (storage/get-one t id)]
        (t/is (identical? x y))))))

(t/deftest identity-map-in-memory
  (storage/with-tx t
    (let [x (storage/create t (->Test 0))
          y (storage/get-one t (aggregate/id @x))]
      (t/is (identical? x y)))))

(t/deftest identity-map-swap
  (storage/with-tx t
    (let [x (storage/create t (->Test 0))
          y (storage/get-one t (aggregate/id @x))
          _ (dosync (alter x update :counter inc))]
      (t/is (= 1 (:counter @x) (:counter @y))))))

(t/deftest concurrency
  (let [test (storage/tx-create (->Test 0))
        id   (aggregate/id test)
        n    10
        _    (->> (repeatedly #(future (storage/tx-alter test update :counter inc)))
                  (take n)
                  (doall)
                  (map deref)
                  (doall))
        test (storage/tx-get-one id)]
    (t/is (= n (:counter test)))))

(t/deftest inner-concurrency
  (let [test (storage/tx-create (->Test 0))
        id   (aggregate/id test)
        n    10
        _    (storage/with-tx t
               (->> (repeatedly #(future (as-> id <>
                                           (storage/get-one t <>)
                                           (dosync (alter <> update :counter inc)))))
                    (take n)
                    (doall)
                    (map deref)
                    (doall)))
        test (storage/tx-get-one id)]
    (t/is (= n (:counter test)))))
```

Наконец, сама фейковая реализация:

```clojure
(ns publicator.use-cases.test.fakes.storage
  (:require
   [publicator.domain.identity :as identity]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.utils.ext :as ext]))

(deftype Transaction [data identity-map]
  storage/Transaction
  (-get-many [_ ids]
    (let [ids-for-select (remove #(contains? @identity-map %) ids)
          selected       (->> ids-for-select
                              (select-keys data)
                              (ext/map-vals identity/build))]
      ;; Здесь принципиально использование reverse-merge,
      ;; т.к. другой поток может успеть извлечь данные из базы,
      ;; создать объект-идентичность, записать его в identity map
      ;; и сделать в нем изменения.
      ;; Если использовать merge, то этот поток затрет идентичность
      ;; другим объектом-идентичностью с начальным состоянием.
      ;; Фактически это нарушает саму идею identity-map -
      ;; сопоставление ссылки на объект с его идентификатором
      (-> identity-map
          (swap! ext/reverse-merge selected)
          (select-keys ids))))

  (-create [_ state]
    (let [id     (aggregate/id state)
          istate (identity/build state)]
      (swap! identity-map (fn [map]
                            {:pre [(not (contains? map id))]}
                            (assoc map id istate)))
      istate)))

(deftype Storage [db]
  storage/Storage
  (-wrap-tx [_ body]
    (loop []
      (let [data         @db
            identity-map (atom {})
            t            (Transaction. data identity-map)
            res          (body t)
            changed      (ext/map-vals deref @identity-map)
            new-data     (merge data changed)]
        (if (compare-and-set! db data new-data)
          res
          (recur))))))

(defn build-db []
  (atom {}))

(defn binding-map [db]
  {#'storage/*storage* (->Storage db)})
```

Эта фейковая реализация хранит все данные в атоме `db`.
Этот атом содержит отображение идентификаторов на состояние сущностей:

```clojure
{1 (->User 1 ...)
 2 (->Post 2 ...)
 3 (->Post 3 ...)}
```

`identity-map` - тоже атом, но содержит отображение идентификаторов на идентичности сущностей:

```clojure
{1 (ref (->User 1 ...))
 2 (ref (->Post 2 ...))}
```
При этом `identity-map` будет содержать не все сущности, что есть в `db`, а только те,
которые участвуют в транзакции.

`-wrap-tx` в бесконечном цикле пытается выполнить транзакцию.
Если с начала транзакции никто не успел поменять данные, то транзакция проходит.
Тут используется оптимистическая блокировка. А помогает в этом низкоуровневая атомарная операция
атома `compare-and-set!`.
