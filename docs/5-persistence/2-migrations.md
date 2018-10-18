# Миграции

Думаю, не стоит рассказывать зачем нужны миграции БД и почему их стоит хранить в системе контроля версий.

Отмечу только, что миграции откатывать нельзя. Как вы откатите удаление колонки?
Чтобы иметь возможность откатить деплой, нельзя ломать обратную совместимость схемы.
Т.е. вы должны иметь возможность запускать новые миграции без выкатки нового кода.
Подробнее в [видео](https://youtu.be/WPCz_U7D8PI?t=3638).

Для миграций я выбрал библиотеку [flyway](https://flywaydb.org/).
Вы можете использовать что-то другое, что лучше подойдет вам.

Оформим запуск миграций в виде компонента, чтобы они автоматически запускались при старте системы:

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

Самостоятельно ознакомьтесь с
[миграциями](https://github.com/darkleaf/publicator/tree/master/persistence/resources/db/migration)
проекта.
