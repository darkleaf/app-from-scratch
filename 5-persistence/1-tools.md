# Инструменты

Здесь перечисляются базовые инструменты, которыми мы будем пользоваться.

## clojure.jdbc

Для работы с БД воспользуемся [clojure.jdbc](https://funcool.github.io/clojure.jdbc/latest).
Кроме нее есть "стандартная" библиотека [clojure/java.jdbc](https://github.com/clojure/java.jdbc).
Первая показалась мне удобнее. О их различиях можно почитать
[тут](https://funcool.github.io/clojure.jdbc/latest/#why-another-jdbc-wrapper).

Пример использования:
```clojure
(require '[jdbc.core :as jdbc])

(with-open [conn (jdbc/connection dbspec)]
  (jdbc/execute conn "CREATE TABLE foo (id serial, name text);"))
```

В качестве `dbspec` можно передавть connection pool.

## Connection pool

clojure.jdbc работает с различными пулами соединений.
Я выбрал [c3p0](https://www.mchange.com/projects/c3p0/).

Оформим пул в компонент:

```clojure
(ns publicator.persistence.components.data-source
  (:require
   [com.stuartsierra.component :as component])
  (:import
   [com.mchange.v2.c3p0 ComboPooledDataSource]))

(defrecord DataSource [config val]
  component/Lifecycle
  (start [this]
    (assoc this :val
           (doto (ComboPooledDataSource.)
             (.setJdbcUrl (:jdbc-url config))
             (.setUser (:user config))
             (.setPassword (:password config)))))
  (stop [this]
    (.close val)
    (assoc this :val nil)))

(defn build [config]
  (DataSource. config nil))
```

## Query builder

Для clojure есть разные sql query builder.
Одни используют data DSL, как [honeysql](https://github.com/jkk/honeysql),
другие - sql, как [hugsql](https://www.hugsql.org/).

Я предпочитаю работать с sql, и не переводить мысленно код из dsl в sql.
Также это позволяет без боли использовать расширения синтаксиса postgresql.

Но никто не запрещает использовать оба подхода в одном приложении.
Data DSL отлично подходит для построения сложного запроса по большому количеству условий.

## Test db

В следующих параграфах будут приводиться тесты и нужно разобраться как готовить базу для тестов.

Предполагается, что база даннных заранее создана и ее настройки передаются через
переменную окружения `TEST_DATABASE_URL`.

Хорошо это или плохо, но тесты в clojure независимы. Нет способа запустить произвольный
код перед запуском тестов. Можно только для каждого неймспейса с тестами прописать
[фикстуры](https://clojuredocs.org/clojure.test/use-fixtures).
Иными словами, для каждгого неймспейса с тестами нужно явно готовить окружение.
В нажем случае это запуск миграций.

Заведем неймспейс с фикстурами, подготавливающими тестовую бд.
`publicator.persistence.test.db` экспортирует 2 функции `once-fixture` и `each-fixture`,
которые мы должны использовать в своих тестах, работающих с БД.

Мы объявляем систему из 2х компонентов: `data-source` и `migration`.

```clojure
(ns publicator.persistence.test.db
  (:require
   [publicator.persistence.components.data-source :as data-source]
   [publicator.persistence.components.migration :as migration]
   [publicator.persistence.utils.env :as env]
   [com.stuartsierra.component :as component]
   [jdbc.core :as jdbc]
   [hugsql.core :as hugsql]
   [hugsql.adapter.clojure-jdbc :as cj-adapter]))

(hugsql/def-db-fns "publicator/persistence/test/db.sql"
  {:adapter (cj-adapter/hugsql-adapter-clojure-jdbc)
   :quoting :ansi})

(defn- build-system []
  (component/system-map
   :data-source (data-source/build (env/data-source-opts "TEST_DATABASE_URL"))
   :migration (component/using (migration/build)
                               [:data-source])))

(defn- with-system [f]
  (let [system (atom (build-system))]
    (try
      (swap! system component/start)
      (f @system)
      (finally
        (swap! system component/stop)))))

(declare ^:dynamic *data-source*)

(defn once-fixture [t]
  (with-system
    (fn [system]
      (let [data-source (-> system :data-source :val)]
        (binding [*data-source* data-source]
          (t))))))

(defn each-fixture [t]
  (try
    (t)
    (finally
      (with-open [conn (jdbc/connection *data-source*)]
        (truncate-all conn)))))
```

Т.е. все тесты неймспейса выполняются в рамках одной системы, а после каждого теста происходит очистка.
Тесты получают доступ к пулу коннектов с помощью динамической переменной `*data-source*`.

```sql
-- db.sql

-- :name- truncate-all :!
DO $$
DECLARE
  statements CURSOR FOR
  SELECT tablename FROM pg_tables
  WHERE schemaname = 'public'
    AND tablename != 'flyway_schema_history';
BEGIN
    FOR stmt IN statements LOOP
        EXECUTE 'TRUNCATE TABLE ' || quote_ident(stmt.tablename);
    END LOOP;
END $$
```
