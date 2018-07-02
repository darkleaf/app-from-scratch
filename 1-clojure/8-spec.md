# Spec

+ https://clojure.org/guides/spec
+ https://clojure.org/about/spec



# Комментарии

При использовании `stest/instrument` проверяются только аргументы функции, но не `:ret` и `:fn`.
Возможно, это поведение изменится, ну а пока можно воспользоваться библиотекой
[orchestra](https://github.com/jeaye/orchestra), которая позиционируется как совместимая
замена `clojure.spec.test.alpha`.

Нужно быть внимательным при описании функций высшего порядка, т.к. instrument
будет проверять соответствие принимаемой функции спецификации. Это допустимо для чистых функций,
но неприемлемо для фукнций с побочным эффектом:

```clojure
(s/fdef f :args (s/cat :g (s/fspec :args (s/cat :x int?)
                                   :ret int?)))

(defn f [g]
  (g 42))

(require '[clojure.spec.test.alpha :as stest])
(stest/instrument `f)

(f str)
;;=> ExceptionInfo Call to #'user/f did not conform to spec:
;;=> In: [0] val: "0" fails at: [:args :g :ret] predicate: int?

(f inc)
;;=> 43

(f (fn [x] (prn x) x))
-1
0
-1
0
-4
-1
-2
-11
-4
0
5
81
-3
196
12
-1853
83
1399
-3
-11
-57026
42
;;=> 42
```

Выход - просто проверять, что аргумент любая функция:

```clojure
(s/fdef f :args (s/cat :g fn?))

```

Хочется использовать spec для валидации форм, но сгенерировать понятные пользователю
сообщения об ошибках из структуры `explain-data` - нетривиальная задача. В этом поможет
библиотека [phrase](https://github.com/alexanderkiel/phrase).

Генератор не всемогущ и использует перебор.
```clojure
(s/def ::login (s/and string? #(re-matches #"\w{3,255}" %)))
(-> ::login s/gen sgen/generate) ;;=> "wbW8"


(s/def ::smile (s/and string? #(re-matches #"[☺☹]" %)))
(-> ::smile s/gen sgen/generate)
;;=> ExceptionInfo Couldn't satisfy such-that predicate after 100 tries.
```

Существуют библиотеки генераторов, которые, например, могут по регулярному выражению
сгенерировать требуемую строку:
[test.chuck](https://github.com/gfredericks/test.chuck),
[strgen](https://github.com/miner/strgen).
