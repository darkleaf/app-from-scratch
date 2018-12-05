# Storage

Напомню абстракцию хранилища:

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

(s/fdef get-one
  :args (s/cat :tx any?
               :id ::id-generator/id)
  :ret (s/nilable ::identity/identity))

(defn get-one [t id]
  (let [res (get-many t [id])]
    (get res id)))

;; ...
```

[Ранее](/02-design/06-persistence.md)
я рассказывал, что есть 2 стратегии выполнения бизнес транзакций: оптимистический и пессимистический.
Эта реализация будет основываться на оптимистической стратегии.

Исходя из специфики бизнес-транзакций можно реализовать и пессимистическую стратегию.
Отмечу, что для этого не нужно переписывать сами бизнес-транзакции.

При использовании оптимистических блокировок мы свободно читаем любые агрегаты,
но запоминаем их версии, а при фиксации проверяем, что версии не изменились.
Если версии изменились, то повторяем бизнес-транзакцию.

Каждому агрегату будет соответствовать свой маппер, который реализует специфичную для
этого агрегата persistence логику.
Каждый маппер должен:

+ извлекать (select) агрегаты по списку их идентификаторов
+ вставлять (insert) агрегаты в базу
+ удалять (delete) агрегаты из базы
+ блокировать агрегат и извлекать его версию

Отмечу, что маппер поддерживает только вставку и удаление, но не изменение.
Для надежности и упрощения кода изменение сведено к удалению и вставке.
Да, это не оптимально с точки зрения производительности, зато просто и надежно.
Если начнутся проблемы с производительностью, можно применить описанную далее оптимизацию.

В конце бизнес-транзакции нужно начать sql транзакцию и вычитать версии изменившихся
агрегатов. Т.к. транзакции идут параллельно, то запрос должен содержать блокировку
`FOR UPDATE`, чтобы другие транзакции дождались изменений в текущей транзакции.

Протокол маппера:

```clojure
(defprotocol Mapper
  (-lock   [this conn ids])
  (-select [this conn ids])
  (-insert [this conn aggregates])
  (-delete [this conn ids]))

(s/def ::mapper #(satisfies? Mapper %))

(s/fdef lock
  :args (s/cat :this ::mapper, :conn any?, :ids (s/coll-of ::id-generator/id))
  :ret (s/coll-of ::versioned-id))

(s/fdef select
  :args (s/cat :this ::mapper, :conn any?, :ids (s/coll-of ::id-generator/id))
  :ret (s/coll-of ::versioned-aggregate))

(s/fdef insert
  :args (s/cat :this ::mapper, :conn any?, :aggregates (s/coll-of ::aggregate/aggregate))
  :ret any?)

(s/fdef delete
  :args (s/cat :this ::mapper, :conn any?, :ids (s/coll-of ::id-generator/id))
  :ret any?)
```

Для тестирования мы будем использовать тестовый агрегат, содержащий одно поле - счетчик:

```clojure
(defrecord TestEntity [id counter]
  aggregate/Aggregate
  (id [_] id)
  (spec [_] any?))
```

Эта сущность будет храниться в таблице:

```sql
CREATE TABLE "test-entity" (
  "id" bigint PRIMARY KEY,
  "counter" integer
);
```

Отмечу, что эта таблица не имеет поля "версия".
В PostgreSQL каждая таблица содержит служебную колонку `xmin`, будем использовать ее
для отслеживания версии, т.к. нам достаточно определить совпадают версии или нет.

`xmin` - Идентификатор (код) транзакции, добавившей строку этой версии.
(Версия строки — это её индивидуальное состояние;
при каждом изменении создаётся новая версия одной и той же логической строки.)
[Подробнее](https://postgrespro.ru/docs/postgrespro/10/ddl-system-columns).

Обратите внимание, `xmin` может переполняться и в этом случае начинает считать сначала.
Иными словами, есть гипотетическая вероятность ложно-положительного сравнения версий,
когда выполняется очень долгая бизнес-транзакция,
а другая транзакция БД с тем же `xmin` изменила нашу запись.

Тестовый маппер использует следующие запросы:

```sql
-- :name- drop-test-entity-table :! :raw
DROP TABLE "test-entity"

-- :name- test-entity-insert :!
INSERT INTO "test-entity" VALUES :tuple*:vals;

-- :name- test-entity-select :? :*
SELECT *, xmin AS version FROM "test-entity" WHERE id IN (:v*:ids)

-- :name- test-entity-delete :!
DELETE FROM "test-entity" WHERE id IN (:v*:ids)

-- :name- test-entity-locks :? :*
SELECT id, xmin AS version FROM "test-entity" WHERE id IN (:v*:ids) FOR UPDATE
```

В процессе исполнения бизнес-транзакции мы отслеживаем какие сущности извлекались, создавались
или изменялись, так же как отслеживали в [фейковой реализации](/03-core/02-use-cases/03-storage).

В конце бизнес-транзакции мы выбираем с блокировкой версии измененных агрегатов,
если версии не изменились, то группируем агрегаты по типу и производим удаление и вставку
с помощью мапперов.


```clojure
(ns publicator.persistence.storage
  (:require
   [jdbc.core :as jdbc]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [publicator.domain.abstractions.id-generator :as id-generator]
   [publicator.domain.identity :as identity]
   [publicator.utils.ext :as ext]
   [clojure.spec.alpha :as s])
  (:import
   [java.util.concurrent TimeoutException]
   [java.time Instant]))

(s/def ::version some?)
(s/def ::versioned-id (s/keys :req-un [::id-generator/id ::version]))
(s/def ::versioned-aggregate (s/keys :req-un [::aggregate/aggregate ::version]))

;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

(defprotocol Mapper
  (-lock   [this conn ids])
  (-select [this conn ids])
  (-insert [this conn aggregates])
  (-delete [this conn ids]))

(s/def ::mapper #(satisfies? Mapper %))

(s/fdef lock
  :args (s/cat :this ::mapper, :conn any?, :ids (s/coll-of ::id-generator/id))
  :ret (s/coll-of ::versioned-id))

(s/fdef select
  :args (s/cat :this ::mapper, :conn any?, :ids (s/coll-of ::id-generator/id))
  :ret (s/coll-of ::versioned-aggregate))

(s/fdef insert
  :args (s/cat :this ::mapper, :conn any?, :aggregates (s/coll-of ::aggregate/aggregate))
  :ret any?)

(s/fdef delete
  :args (s/cat :this ::mapper, :conn any?, :ids (s/coll-of ::id-generator/id))
  :ret any?)

(defn- default-for-empty [f default]
  (fn [this conn coll]
    (if (empty? coll)
      default
      (f this conn coll))))

(def lock   (default-for-empty -lock   []))
(def select (default-for-empty -select []))
(def insert (default-for-empty -insert nil))
(def delete (default-for-empty -delete nil))

;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

(defrecord Transaction [data-source mappers identity-map]
  storage/Transaction
  (-get-many [this ids]
    (with-open [conn (jdbc/connection data-source)]
      (let [ids-for-select (remove #(contains? @identity-map %) ids)
            selected       (->> mappers
                                (vals)
                                (mapcat #(select % conn ids-for-select))
                                (map (fn [{:keys [aggregate version]}]
                                       (let [iaggregate (identity/build aggregate)]
                                         (alter-meta! iaggregate assoc
                                                      ::version version
                                                      ::initial aggregate)
                                         iaggregate)))
                                (group-by #(-> % deref aggregate/id))
                                (ext/map-vals first))]
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
            (select-keys ids)))))

  (-create [this state]
    (let [id     (aggregate/id state)
          istate (identity/build state)]
      (swap! identity-map (fn [map]
                            {:pre [(not (contains? map id))]}
                            (assoc map id istate)))
      istate)))

(defn- build-tx [data-source mappers]
  (Transaction. data-source mappers (atom {})))

(defn- need-insert? [identity]
  (not= @identity
        (-> identity meta ::initial)))

(defn- need-delete? [identity]
  (let [initial (-> identity meta ::initial)]
    (and (some? initial)
         (not= @identity initial))))

(defn- lock-all [conn mappers identities]
  (let [ids             (->> identities
                             (vals)
                             (filter need-delete?)
                             (map deref)
                             (map aggregate/id))
        db-versions     (->> mappers
                             (vals)
                             (mapcat #(lock % conn ids))
                             (group-by :id)
                             (ext/map-vals #(-> % first :version)))
        memory-versions (->> (select-keys identities ids)
                             (ext/map-vals #(-> % meta ::version)))]
    (= db-versions memory-versions)))

(defn- delete-all [conn mappers identities]
  (let [groups (->> identities
                    (vals)
                    (filter need-delete?)
                    (map deref)
                    (group-by class)
                    (ext/map-keys #(get mappers %))
                    (ext/map-vals #(map aggregate/id %)))]
    (doseq [[manager ids] groups]
      (delete manager conn ids))))

(defn- insert-all [conn mappers identities]
  (let [groups (->> identities
                    (vals)
                    (filter need-insert?)
                    (map deref)
                    (group-by class)
                    (ext/map-keys #(get mappers %)))]
    (doseq [[manager aggregates] groups]
      (insert manager conn aggregates))))

(defn- commit [tx mappers]
  (let [data-source (:data-source tx)
        identities  @(:identity-map tx)]
    (with-open [conn (jdbc/connection data-source)]
      (jdbc/atomic conn
                   (when (lock-all conn mappers identities)
                     (delete-all conn mappers identities)
                     (insert-all conn mappers identities)
                     true)))))

(defn- timestamp []
  (inst-ms (Instant/now)))

(deftype Storage [data-source mappers opts]
  storage/Storage
  (-wrap-tx [this body]
    (let [soft-timeout (get opts :soft-timeout-ms 500)
          stop-after   (+ (timestamp) soft-timeout)]
      (loop [attempt 0]
        (let [tx       (build-tx data-source mappers)
              res      (body tx)
              success? (commit tx mappers)]
          (cond
            success?                   res
            (< (timestamp) stop-after) (recur (inc attempt))
            :else                      (throw (TimeoutException.
                                               (str "Can't run transaction after "
                                                    attempt " attempts")))))))))



(s/fdef binding-map
  :args (s/cat :data-source any?
               :mappers (s/map-of class? ::mapper)
               :opts (s/? map?))
  :ret map?)

(defn binding-map
  ([data-source mappers]
   (binding-map data-source mappers {}))
  ([data-source mappers opts]
   {#'storage/*storage* (Storage. data-source mappers opts)}))
```

Тест повторяет тест [фейковой реализации](/03-core/02-use-cases/03-storage):

```clojure
(ns publicator.persistence.storage-test
  (:require
   [publicator.persistence.types]
   [publicator.utils.test.instrument :as instrument]
   [clojure.test :as t]
   [hugsql.core :as hugsql]
   [hugsql.adapter.clojure-jdbc :as cj-adapter]
   [jdbc.core :as jdbc]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.persistence.test.db :as db]
   [publicator.persistence.storage :as sut]))

(defrecord TestEntity [id counter]
  aggregate/Aggregate
  (id [_] id)
  (spec [_] any?))

(defn build-test-entity []
  (TestEntity. 42 0))

;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

(hugsql/def-db-fns "publicator/persistence/storage_test.sql"
  {:adapter (cj-adapter/hugsql-adapter-clojure-jdbc)})

(defn- aggregate->sql [aggregate]
  (vals aggregate))

(defn- row->versioned-aggregate [row]
  {:aggregate (-> row (dissoc :version) map->TestEntity)
   :version   (-> row (get :version))})

(def mapper (reify sut/Mapper
              (-lock [_ conn ids]
                (test-entity-locks conn {:ids ids}))
              (-select [_ conn ids]
                (map row->versioned-aggregate (test-entity-select conn {:ids ids})))
              (-insert [_ conn states]
                (test-entity-insert conn {:vals (map aggregate->sql states)}))
              (-delete [_ conn ids]
                (test-entity-delete conn {:ids ids}))))

;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

(defn- setup [t]
  (with-bindings (sut/binding-map db/*data-source* {TestEntity mapper})
    (t)))

(defn- test-table [t]
  (with-open [conn (jdbc/connection db/*data-source*)]
    (drop-test-entity-table conn)
    (create-test-entity-table conn))
  (t))

(t/use-fixtures :once
  instrument/fixture
  db/once-fixture)

(t/use-fixtures :each
  db/each-fixture
  test-table
  setup)

(t/deftest create
  (let [entity (storage/tx-create (build-test-entity))]
    (t/is (some? (storage/tx-get-one (aggregate/id entity))))))

(t/deftest change
  (let [entity (storage/tx-create (build-test-entity))
        _      (storage/tx-alter entity update :counter inc)
        entity (storage/tx-get-one (:id entity))]
    (t/is (= 1 (:counter entity)))))

(t/deftest identity-map-persisted
  (let [id (:id (storage/tx-create (build-test-entity)))]
    (storage/with-tx t
      (let [x (storage/get-one t id)
            y (storage/get-one t id)]
        (t/is (identical? x y))))))

(t/deftest identity-map-in-memory
  (storage/with-tx t
    (let [x (storage/create t (build-test-entity))
          y (storage/get-one t (aggregate/id @x))]
      (t/is (identical? x y)))))

(t/deftest identity-map-swap
  (storage/with-tx t
    (let [x (storage/create t (build-test-entity))
          y (storage/get-one t (aggregate/id @x))]
       (dosync (alter x update :counter inc))
      (t/is (= 1 (:counter @x) (:counter @y))))))

(t/deftest concurrency
  (let [test (storage/tx-create (build-test-entity))
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
  (let [test (storage/tx-create (build-test-entity))
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

## Оптимизация

Например, у нас есть агрегат Пост, содержащий вложенные комментарии.
Пост и Комментарий сохраняются в отдельных таблицах.
Для начальной и новой версии агрегата нужно сгенерировать списки операций вставки:

```clojure
;; initial
[[:post {:id 1, :title "123", :content "123"}]
 [:comment {:id 1, :title "awesome!"}]]

;; current
[[:post {:id 1, :title "123", :content "123 - additional text"}]
 [:comment {:id 1, :title "awesome!"}]]
```

Сравнивая эти списки получаем набор sql операций.
В данном случае нужно только удалить и вставить строку с постом,
т.к. комментарии не изменились.

## Mappers

Самостоятельно разберите мапперы Пользователя и Поста:

+ [миграции](https://github.com/darkleaf/publicator/tree/master/persistence/resources/db/migration)
+ [преобразование типов](https://github.com/darkleaf/publicator/blob/master/persistence/src/publicator/persistence/types.clj)
+ [реализация](https://github.com/darkleaf/publicator/tree/master/persistence/src/publicator/persistence/storage)
+ [тесты](https://github.com/darkleaf/publicator/tree/master/persistence/test/publicator/persistence/storage)
