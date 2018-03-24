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

Добавляет поддержку запуска тестов с помощью команд:

+ `:repl/run-tests`
