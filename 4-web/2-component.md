# Управление stateful компонентами

Если не использовать интерактивную разработку в repl и на каждое изменение кода
перезапускать jvm, то проблем не возникает, т.к. все ресурсы освобождаются при завершении
jvm процесса.

Теперь мы хотим перезагружать наши неймспейсы не перезапуская jvm.
Для этого воспользуемся [tools.namespace](https://github.com/clojure/tools.namespace).

Допустим, у нас есть неймспейс:

```clojure
(ns app.app
  (:require
   [clojure.pprint :as pp]
   [ring.adapter.jetty :as jetty]))

(defn handler [req]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (with-out-str (pp/pprint req))})

(jetty/run-jetty handler {:port 4445})
```

При перезагрузке он очищается и его код выполняется заново.
При этом будет неудачная попытка запуска сервера с новым обработчиком,
т.к. старый сервер не остановлен и занимает порт.

Для stateless кода перезагрузка работает тривиально, а для stateful кода нужно освобождать ресурсы.
Поэтому stateful код изолируют с помощью компонентов.

Компонент - нечто, что можно запустить и остановить. Компоненты зависят от других компонентов
и задача фреймворка в правильном порядке запускать и останавливать компоненты.

В clojure есть 2 популярных проекта для управления stateful компонентами.
+ [component](https://github.com/stuartsierra/component)
+ [mount](https://github.com/tolitius/mount)

Component внутренне проще, функциональнее, но несколько сложнее в использовании, а mount - наоборот.

Наше приложение состоит из ядра и различных плагинов.
Например, приложение может работать с фейковым хранилищем в памяти и
персистентным хранилищем в postgresql.
В случае с component становится тривиальным запуск 2х копий приложения
на разных портах с разными хранилищами, в случае с mount - это не так.

Фреймворк компонентов - вспомогательная библиотека для нашего приложения.
Ни логика, ни реализации абстракций не зависят от этого фреймворка.
Поэтому для своего приложения вы можете выбрать любую библиотеку или написать свою.
Сейчас же я буду использовать component.

## Component

Component использует записи, реализущие протокол `Lifecycle` с 2 методами: `start` и `stop`.
Используются записи, а не типы, т.к. зависимости компонента устанавливаются через `assoc`.
Таким образом компоненты объединяются в Систему. Таким образом можно запускать и останавливать
компоненты в порядке их зависимости друг от друга.

Так же Component позиционируется как фреймворк для внедрения зависимостей.
Но в этом случае наше приложение жестко зависит от этой библиотеки, что не приемлемо для нас.
К тому же мы уже используем dynamic var для внедрения зависимостей.

Продробнее узнать про компонент:

+ https://github.com/stuartsierra/component
+ https://www.youtube.com/watch?v=13cmHf_kt-Q
+ https://github.com/matthiasn/talk-transcripts/blob/master/Sierra_Stuart/Components.md

С component наше приложение будет выглядеть так:

```clojure
(ns app.app
  (:require
   [clojure.pprint :as pp]
   [com.stuartsierra.component :as component]
   [ring.adapter.jetty :as jetty]))

(defn handler [req]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (with-out-str (pp/pprint req))})

(defrecord Jetty [val]
  component/Lifecycle
  (start [this]
    (if val
      this
      (assoc this :val
             (jetty/run-jetty #'handler
                              {:port 4445
                               :join? false}))))
  (stop [this]
    (if-not val
      this
      (do
        (.stop val)
        (assoc this :val nil)))))

(defn build-jetty []
  (->Jetty nil))
```

## Перезагрузка

Перед перезагрузкой нужно остановить приложени, а после - запустить.
Подробнее про этот механизм можно прочитать в readme
[tools.namespace](https://github.com/clojure/tools.namespace).

Плюс приложение нужно как-то запустить в начале работы.

Когда мы открываем repl, то начинаем в пространстве имен `user`.
Удобно добавить в него функции `start` и `stop`.
Добавим файл `dev/user.clj`, и добавим директорию `dev` в пути поиска неймспейсов:

```clojure
;; dev/user.clj
(ns user
  (:require
   [com.stuartsierra.component :as component]
   [app.app :as app]))

(def component (app/build-jetty))

(defn start []
  (alter-var-root #'component component/start))

(defn stop []
  (alter-var-root #'component component/stop))
```

```edn
;; deps.edn
{:deps {ring/ring-core             {:mvn/version "1.6.2"}
        ring/ring-jetty-adapter    {:mvn/version "1.6.2"}
        com.stuartsierra/component {:mvn/version "0.3.2"}}

 :paths ["dev" "src"]} ;; добавляем директорию dev
```

Наш компонент хранится в переменной `component`. Она инициализируется при загрузке неймспейса
незапущенным компонентом. А функции `start` и `stop` заменяют значение этой переменной.

Для разработки мы можем использовать либо просто repl, либо emacs + cider:

+ clojure -Arepl
+ clojure -Acider

И нужно как-то указать им какие функции вызывать перед и после перезагрузки.

Для emacs воспользуемся файлом `.dir-locals.el` в [корне директории с примерами](sources):

```elisp
((nil
  (eval .
        (setq cider-refresh-before-fn "user/stop"
              cider-refresh-after-fn "user/start"))))
```

А в repl можно передать их как аргумент. [deps.edn](sources/docker-clojure/deps.edn):

```edn
{:aliases {:repl  {:extra-deps {darkleaf/repl-tools-deps
                                {:git/url "https://github.com/darkleaf/repl-tools-deps.git"
                                 :sha     "04e128ca67785e4eb7ccaecfdaffa3054442358c"}}
                   :main-opts  ["-m" "darkleaf.repl-tools-deps"
                                "reload-before-fn" "user/stop"
                                "reload-after-fn" "user/start"]}}}
```

Теперь в emacs можно делать перезагрузку с помощью `C-c C-x`, а в repl - `:repl/reload`.

## Перезагрузка без потери состояния

Важно отметить, что перезагрузка убирает любое состояние.
Например, мы запустили приложение с вебсервером и фейковым хранилищем.
Внесли какие-то данные, например создали пост, внесли правки в код, перезагрузили.
А т.к. данные хранятся в памяти, то наш пост будет утерян.

Вебсервер запускается следующим образом: `(jetty/run-jetty handler {:port 4445})`.
Т.е. мы передаем обработчик. Но в этом случае мы передаем значение обработчика,
а не переменную, содержащую обработчик. Если мы зададим новое значение этой переменной,
вебсервер будет использовать старое значение.

Благодаря тому, что сама переменная поддерживает интерфейс функции, можно передавать саму переменную:
`(jetty/run-jetty #'handler {:port 4445})`.
В этом случае при каждом запросе будет использовать обработчик, содержащийся в переменной.

Это дает возможность менять код, видеть изменеия, но не перезагружать все приложение.
