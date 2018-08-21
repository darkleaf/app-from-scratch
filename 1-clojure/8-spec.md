# Spec

Начиная с версии 1.9 clojure поставляется с библиотекой
[clojure.spec](https://github.com/clojure/spec.alpha).
Мы получили механизм создания спецификаций для данных и функций.

Можно проверять данные и функции на соответствие спецификаций,
генерировать данные на их основе, использовать генеративные тесты и т.п.

Ознакомьтесь с официальными материалами:

+ [rationale and overview](https://clojure.org/about/spec)
+ [spec guide](https://clojure.org/guides/spec)

В качестве практики поэкспериментируйте со спецификаций поста.
[Исходники примера](/sources/1-clojure/8-spec).

```clojure
(ns spec.post
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sgen]
   [clojure.spec.test.alpha :as st]))

(s/def ::id int?)
(s/def ::title (s/and string? #(re-matches #".{1,255}" %)))
(s/def ::content string?)
(s/def ::created-at inst?)

(s/def ::post (s/keys :req-un [::id ::title ::content ::created-at]))

(s/fdef build
  :args (s/cat :params (s/keys :req-un [::title ::content]))
  :ret ::post)

(let [counter (atom 0)]
  (defn build [{:keys [title content]}]
    {:id         (swap! counter inc)
     :title      title
     :content    content
     :created-at (java.util.Date.)}))

(comment
  (s/valid? ::post {}) ;; #=> false

  ;; returned data:
  ;; #:clojure.spec.alpha{:problems
  ;;                      ({:path [],
  ;;                        :pred (clojure.core/fn [%]
  ;;                                (clojure.core/contains? % :content)),
  ;;                        :val {:id 1, :title "", :created-at 2018},
  ;;                        :via [:spec.post/post],
  ;;                        :in []}
  ;;                       {:path [:title],
  ;;                        :pred (clojure.core/fn [%]
  ;;                                (clojure.core/re-matches #".{1,255}" %)),
  ;;                        :val "",
  ;;                        :via [:spec.post/post :spec.post/title],
  ;;                        :in [:title]}
  ;;                       {:path [:created-at],
  ;;                        :pred clojure.core/inst?,
  ;;                        :val 2018,
  ;;                        :via [:spec.post/post :spec.post/created-at],
  ;;                        :in [:created-at]}),
  ;;                      :spec :spec.post/post,
  ;;                      :value {:id 1, :title "", :created-at 2018}}
  (s/explain-data ::post {:id 1, :title "", :created-at 2018}))

(comment
  (-> ::post
      s/gen
      sgen/generate))

(comment
  (build {:title "Hello"})

  (st/instrument)

  ;; Boom!
  ;; Spec assertion failed
  ;; Problems:
  ;;      val: {:title "Hello"}
  ;;       in: [0]
  ;;   failed: (contains? % :content)
  ;;       at: [:args :params])
  (build {:title "Hello"}))
```

# Комментарии

Спецификации напоминают ститическю типизацию, только провеки выполняются в рантайме.
Однако, есть экспериментальный проект [spectrum](https://github.com/arohner/spectrum)
запускающий проверки спецификаций в compile time.

***

При использовании `st/instrument` проверяются только аргументы функции, но не `:ret` и `:fn`.
Возможно, это поведение изменится, но а пока можно воспользоваться библиотекой
[orchestra](https://github.com/jeaye/orchestra), которая позиционируется как
замена `clojure.spec.test.alpha`.

***

Нужно быть внимательным при описании функций высшего порядка, т.к. instrument
будет проверять соответствие спецификации принимаемой или возвращаемой функции.
Это допустимо для чистых функций, но неприемлемо для фукнций с побочным эффектом:

```clojure
(s/fdef f
   :args (s/cat :g (s/fspec
                     :args (s/cat :x int?)
                     :ret int?)))

(defn f [g]
  (g 42))

(require '[clojure.spec.test.alpha :as st])
(st/instrument `f)

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

***

Instrument не работает для протоколов.
Используйте обертки:

```clojure
(defprotocol P
  (-foo [x y z]))

(s/fdef foo ...)

(defn foo [x y z]
  (-foo x y z))
```

***

Хочется использовать spec для валидации форм, но сгенерировать понятные пользователю
сообщения об ошибках из структуры `explain-data` - нетривиальная задача. В этом поможет
библиотека [phrase](https://github.com/alexanderkiel/phrase).

***

Генератор не всемогущ и использует перебор:

```clojure
(s/def ::login (s/and string? #(re-matches #"\w{3,255}" %)))
(-> ::login s/gen sgen/generate) ;;=> "wbW8"

;; UTF symbols
(s/def ::smile (s/and string? #(re-matches #"[☺☹]" %)))
(-> ::smile s/gen sgen/generate)
;;=> ExceptionInfo Couldn't satisfy such-that predicate after 100 tries.
```

Существуют библиотеки генераторов, которые, например, могут по регулярному выражению
сгенерировать требуемую строку:
[test.chuck](https://github.com/gfredericks/test.chuck),
[strgen](https://github.com/miner/strgen).
