# Миграции

Думаю, не стоит рассказывать зачем нужны миргации БД и зачем их хранить в системе контроля версий.

Отмечу только, что миграции откатывать нельзя.
Как вы откатите удаление колонки?

Чтобы иметь возможность откатить депой, код приложения должен работать на старой и новой схеме БД.
Подробнее в [видео](https://youtu.be/WPCz_U7D8PI?t=3638).

Для clojure/java есть несколько библиотек для миграций, я выбрал [flyway](https://flywaydb.org/).

Оформим запуск миргаций в виде компонента, чтобы они автоматически запускались при старте системы:

```clojure
(ns publicator.persistence.components.migration
  (:require
   [com.stuartsierra.component :as component])
  (:import
   [org.flywaydb.core Flyway]))

(defrecord Migration [data-source]
  component/Lifecycle
  (start [this]
    (doto (Flyway.)
      (.setDataSource (:val data-source))
      (.migrate))
    this)
  (stop [this]
    this))

(defn build []
  (Migration. nil))
```

Flyway автоматически загружает файлы миграций из classpath, поэтому добавим `recources` в файл deps.edn:

```edn
{:paths   ["src" "resources"]}
```

Миграции хранятся в директории `resources/db/migration` и должны иметь определенные имена,
вроде `V1__id_sequence.sql`.
