# Storage


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
        ;; Если использовать merge, то этот поток затрет идентчиность
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

```sql
-- storage_test.sql

-- :name- create-test-entity-table :! :raw
CREATE TABLE "test-entity" (
  "id" bigint PRIMARY KEY,
  "counter" integer
);

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

```clojure
(ns publicator.persistence.storage-test
  (:require
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

(defn- sql->version [raw]
  (.getValue raw))

(defn- sql->aggretate [raw]
  (map->TestEntity raw))

(defn- aggregate->sql [aggregate]
  (vals aggregate))

(defn- row->versioned-aggregate [row]
  {:aggregate (-> row (dissoc :version) sql->aggretate)
   :version   (-> row (get :version) sql->version)})

(defn- row->versioned-id [{:keys [id version]}]
  {:id      id
   :version (sql->version version)})

(def mapper (reify sut/Mapper
              (-lock [_ conn ids]
                (map row->versioned-id (test-entity-locks conn {:ids ids})))
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
    (create-test-entity-table conn)
    (try
      (t)
      (finally
        (drop-test-entity-table conn)))))

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

## Mappers

самостоятельно
ссылка на миграции
