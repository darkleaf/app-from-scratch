Обертка над [bhauman/rebel-readline](https://github.com/bhauman/rebel-readline).

Добавляет поддержку [reloading](https://github.com/clojure/tools.namespace/) с помощью команд

+ `:repl/reload`
+ `:repl/reload-all`

```edn
{:aliases
 {:repl {:extra-deps {darkleaf/repl {:local/root "../repl"}}
         :main-opts ["-m" "darkleaf.repl"]}}
}
```

или

```edn
{:aliases
 {:repl {:extra-deps {darkleaf/repl {:local/root "../repl"}}
         :main-opts ["-m" "darkleaf.repl"
                     "reload-before-fn" "user/stop"
                     "reload-after-fn" "user/start"]}}
}
```


Если при перезагрузке произошла ошибка, то она доступна как `*e`:

```
:repl/reload-all
:reloading (some.namespace)
:error-while-loading some.namespace
user=> *e
#error {
...
}
```

Добавляет поддержку запуска тестов с помощью команд:

+ `:repl/run-tests`
