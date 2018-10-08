# Инструменты

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
