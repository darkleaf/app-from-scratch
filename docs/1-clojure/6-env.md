# Окружение для разработки

Ранее мы использовали сервис repl.it и теперь настал момент для установки полноценного окружения.

## tools.deps

Для управления зависимостями воспользуемся утилитой
[tools.deps](https://github.com/clojure/tools.deps.alpha)
от разработчиков clojure.

Изначально clojure не имела утилит и распространялась в
виде одного jar файла. И зависимостями управляли с помощью maven.
Позднее появились сторонние системы сборки написанные на clojure:
[lein](https://leiningen.org/) и
[boot](http://boot-clj.com/).
Кроме того есть диалект clojurescript, компилирующийся в javascript,
но для него библиотеки тоже распространяются в виде jar файлов, а
не npm пакетов.
С релизом 1.9 clojure распространяется в виде 3‑х jar файлов
и встал вопрос об официальной утилите и собственном формате пакетов.

Итак, tools.deps позволяет:

+ подключать зависимости из:
  + maven репозиториев
  + git репозиториев
  + локальных jar файлов
  + локальных подпроектов
+ строить java class path на их основе
+ запускать repl
+ задавать различные entry point, подобно разделу `scripts` в `package.json`

Он хранит конфигурацию проекта в файле `deps.edn`, размещенном в корне проекта.
Также можно создать файл `~/.clojure/deps.edn`, который будет использоваться для всех проектов.
В нем стоит указывать конфигурацию, специфичную для вас - версии repl и т.п.

[EDN](https://github.com/edn-format/edn) расшифровывается как extensible data notation.
Он использует clojure синтаксис и поддерживает все структуры данных clojure.
Можно провести аналогию форматом JSON, который использует javascript синтаксис.

Прежде чем двигаться дальше стоит изучить документацию:

+ https://clojure.org/guides/getting_started
+ https://clojure.org/guides/deps_and_cli
+ https://clojure.org/reference/deps_and_cli

## Docker

Есть готовые образы: https://hub.docker.com/_/clojure/

```
# alpine
run --rm -it clojure:tools-deps-alpine clojure
```

```
# debian
run --rm -it clojure:tools-deps clojure
```

## Repl

Т.к. стандартный repl малофункционален, то мы воспользуемся [rebel-readline](https://github.com/bhauman/rebel-readline) вместо него.

Этот repl легко расширяется, и я сделал собственный вариант, который позволяет:

+ перезагружать код в измененных файлах
+ запускать тесты

Подробности - https://github.com/darkleaf/repl-tools-deps

Если вы не работали с Emacs, и не планируете его изучение - это ваш выбор.

Но если вы никогда не использовали редактор с интегрированным repl,
то [вы живете неправильно](https://tonsky.livejournal.com/316868.html).

## Emacs + Cider

Наверняка есть и другие редакторы с поддержкой интеграции с repl, но Emacs - by design ориентирован
на интерактивную разработку и lisp подобные языки.

[cider](http://docs.cider.mx/en/latest/) - пакет для Emacs, превращающий его в полноценную clojure IDE.

Способ подключения [cider-nrepl](https://github.com/clojure-emacs/cider-nrepl) через tools.deeps,
описанный в readme, не работает и к тому же не позволяет задать порт и хост на котором запустится сервер nrepl.

Я написал простую обертку - https://github.com/darkleaf/cider-tools-deps

## Parinfer

Расставлять и выравнивать скобки - неблагодарное занятие.
Но есть плагин для множества редакторов, облегчающий редактирование lisp выражений:

https://shaunlebron.github.io/parinfer/

## Code reloading

Для clojure есть библиотека для перезагрузки кода без перезагрузки jvm процесса.
Это [tools.namespace](https://github.com/clojure/tools.namespace).
Ее использует и cider и repl-tools-deps.

Пока мы будем работать с stateless кодом, а он перезагружается тривиально.
В дальнейшем мы столкнемся с stateful кодом и я покажу как с ним работать.

Для перезагрузки кода используйте:

+ `:repl/reload` для repl-tools-deps
+ `C-c C-x` для cider.

## Примеры кода

По ходу изложения будут даваться примеры кода.
Все они доступны в директории [sources](https://github.com/darkleaf/app-from-scratch/tree/master/sources).
В readme даны инструкции по запуску repl/cider.
