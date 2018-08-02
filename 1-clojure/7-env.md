# tools.deps.alpha

Для управления зависимостями воспользуемся утилитой
[tools.deps.alpha](https://github.com/clojure/tools.deps.alpha)
от разработчиков clojure.

Изначально clojure не имела утилит и распространялась в
виде одного jar файла. И зависимостями управляли с помощью maven.
Позднее появились строронние системы сборки написанные на clojure:
[lein](https://leiningen.org/) и
[boot](http://boot-clj.com/).
Кроме того есть диалект clojurescript, компилирующийся в javascript,
но для него библиотеки тоже распространяются в виде jar файлов, а
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


Прежде чем двигаться дальше нужно изучить документацию:

+ https://clojure.org/guides/getting_started
+ https://clojure.org/guides/deps_and_cli
+ https://clojure.org/reference/deps_and_cli

# Docker

Есть готовые образы: https://hub.docker.com/_/clojure/

```
# alpine
run --rm -it clojure:tools-deps-alpine clojure
```

```
# debian
run --rm -it clojure:tools-deps clojure
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

Он расширяется, и я сделал собственный вариант, который умеет:

+ перезагружать код из измененных файлов
+ запускать тесты

Подробности - https://github.com/darkleaf/repl-tools-deps

Если вы не работали с emacs, и не планируете его изучение - это ваш выбор.

# Emacs + Cider

Наверняка есть и другие реадкторы с поддержкой интеграции с repl, но emacs - by design ориентирован
на интерактивную разработку и lisp подобные языки.

Если вы никогда не использовали редактор с интегрированным repl,
то [вы живете неправильно](https://tonsky.livejournal.com/316868.html).

[cider](http://docs.cider.mx/en/latest/) - пакет для emacs, превращающий его в полноценную IDE.

Способ подключения [cider-nrepl](https://github.com/clojure-emacs/cider-nrepl) через tools.deeps,
описанный в readme, не работает и к тому же не позволяет задать порт и хост на котором запустится сервер nrepl.

Я написал простую обертку - https://github.com/darkleaf/cider-tools-deps

# Parinfer

Расставлять и выравнивать скобки - неблагодарное занятие.
Но есть плагин для множества редакторов, облегчающий редактирование lisp выражений:

https://shaunlebron.github.io/parinfer/

# Code reloading

Для clojure есть механизм перезагрузки кода без перезагрузки jvm процесса:

https://github.com/clojure/tools.namespace

Для stateless кода все тривиально. А как перезагружать stateful код я расскажу позже,
когда появится такая необходимость.
