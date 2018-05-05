# tools.deps.alpha

Для управления зависимостями воспользуемся утилитой
[tools.deps.alpha](https://github.com/clojure/tools.deps.alpha).

Изначально clojure не имала утилит и распространялась в
виде одного jar файла. И зависимостями управляли с помощью maven.
Позднее появились системы сборки написанные на clojure:
[lein](https://leiningen.org/) и
[boot](http://boot-clj.com/).
Кроме того есть диалект clojurescript, компилирующийся в javascript,
но для него библиотеки распространяются тоже в виде jar файлов, а
не npm пакетов.
С релизом 1.9 clojure распространяется в виде 3х jar файлов
и встал вопрос об официальной утилите и собственном формате пакетов.

Итак, tools.deps позволяет:

+ подклюичать зависимости из:
  + maven репозиториев
  + git репозиториев
  + локальных jar файлов
  + локальных подпроектов
+ строить java class path на их основе
+ запускать repl
+ задавать различные entry point, подобно разделу `scripts` в `package.json`

Он хранит конфигурацию проекта в файле `deps.edn` размещенном в корне проекта.

[EDN](https://github.com/edn-format/edn) расшифровывается как extensible data notation.
Он использует clojure синтаксис и поддерживает все структуры данных clojure.
Можно провести аналогию форматом JSON, который использует javascript синтаксис.

Документация содержит исчерпывающие [guide](https://clojure.org/guides/deps_and_cli)
и [reference](https://clojure.org/reference/deps_and_cli). Необходимо изучить эти материалы
прежде чем двигаться дальше.

# Docker

Если вы умеете работать с docker, то можете воспользоватся следующим Dockerfile:

```
FROM openjdk:8-jre-alpine

RUN apk add --update --no-cache curl bash

RUN curl -O https://download.clojure.org/install/linux-install-1.9.0.358.sh \
 && sh linux-install-1.9.0.358.sh \
 && rm linux-install-1.9.0.358.sh
```

# Связь неймспейсов и файлов

Clojure использует
+ `lisp-case` символов и кейвородов
+ `CamelCase` для записей, типов, протоколов и взаимодействия с java

Каждый файл содержит один неймспейс. При этом `lisp-case` преобразуется в `snake_case`.
Например, неймспейс `project-name.cool-ns` будет записан в файле
`src/project_name/cool_ns.clj`.

# Repl

Воспользуемся нестандартным repl [rebel-readline](https://github.com/bhauman/rebel-readline).

Он расширяется и я сделал собственный вариант, который умеет:

+ перезагружать код из измененных файлов
+ запускать тесты

Подробности - https://github.com/darkleaf/repl-tools-deps

# Cider

Если вы знакомы с emacs, то стоит для разработки использовать [cider](https://cider.readthedocs.io/en/latest/).
Фактически, это IDE для clojure.

Способ подключения [cider-nrepl](https://github.com/clojure-emacs/cider-nrepl) через tools.deeps,
описанный в readme, не работает и к тому же не позволяет задать порт и хост на котором запустится сервер nrepl.

Я написал простую обертку - https://github.com/darkleaf/cider-tools-deps

# Code reloading

https://github.com/clojure/tools.namespace

# Parinfer

Плагин для множества редакторов, облегчающий редактирование lisp

https://shaunlebron.github.io/parinfer/
