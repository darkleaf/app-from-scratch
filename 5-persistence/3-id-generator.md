# Id generator

Напомню, что `id-generator` имеет один метод `generate`, который возвращает
положительное целое число:

```clojure
(ns publicator.domain.abstractions.id-generator
  (:require
   [clojure.spec.alpha :as s]))

(defprotocol IdGenerator
  (-generate [this]))

(declare ^:dynamic *id-generator*)

(s/def ::id pos-int?)

(s/fdef generate
  :ret ::id)

(defn generate []
  (-generate *id-generator*))
```

В PostgreSQL для генерации идентификаторов используют
[sequence](https://postgrespro.ru/docs/postgrespro/10/sql-createsequence).

Создадим ее первой миграцией:

```sql
-- persistence/resources/db/migration/V1__id_sequence.sql
CREATE SEQUENCE "id-generator";
```

Генерируется новый идентификатор следующим запросом:
```sql
SELECT nextval('id-generator') AS id
```

Добавим реализацию протокола `IdGenerator`:

```clojure
(ns publicator.persistence.id-generator
  (:require
   [jdbc.core :as jdbc]
   [publicator.domain.abstractions.id-generator :as id-generator]))

(deftype IdGenerator [data-source]
  id-generator/IdGenerator
  (-generate [_]
    (with-open [conn (jdbc/connection data-source)]
      (let [stmt (jdbc/prepared-statement conn "SELECT nextval('id-generator') AS id")
            resp (jdbc/fetch-one conn stmt)]
        (:id resp)))))

(defn binding-map [datasource]
  {#'id-generator/*id-generator* (->IdGenerator datasource)})
```

И его тест:

```clojure
(ns publicator.persistence.id-generator-test
  (:require
   [clojure.test :as t]
   [publicator.domain.abstractions.id-generator :as id-generator]
   [publicator.utils.test.instrument :as instrument]
   [publicator.persistence.test.db :as db]
   [publicator.persistence.id-generator :as sut]))

(defn- setup [t]
  (with-bindings (sut/binding-map db/*data-source*)
    (t)))

(t/use-fixtures :once
  instrument/fixture
  db/once-fixture)

(t/use-fixtures :each
  db/each-fixture
  setup)

(t/deftest generate
  (t/is (pos-int? (id-generator/generate))))
```
