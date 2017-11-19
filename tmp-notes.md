DDD так же вводит понятие агрегата.
Агрегат это совокупность взаимосвязанных объектов,
которые мы воспринимаем как единое целое с точки зрения изменения данных.
Сейчас для нас важно только одно свойство агрегатов.
Агрегат должен контроллировать соблюдение инвариантов.
Инварианты удобно описать спомощью
[спецификаций(clojure.spec)](https://clojure.org/about/spec),
которые появились в Clojure 1.9.

```clojure
(s/def ::id ::id-generator/id)
(s/def ::login (s/and string? #(re-matches #"\w{3,255}" %)))
(s/def ::full-name (s/and string? #(re-matches #".{2,255}" %)))
(s/def ::password-digest ::hasher/encrypted)

(s/def ::attrs (s/keys :req-un [::id ::login ::full-name ::password-digest]))
```

Теперь свяжем Пользователя и спецификацию. Для этого определим прототокол агрегата:

```clojure
(ns publicator.domain.protocols.aggregate
  (:refer-clojure :exclude [assert])
  (:require
   [clojure.spec.alpha :as s]))

(defprotocol Aggregate
  (spec [this] "Aggregate validation spec"))

(defn assert [agg]
  {:pre [(satisfies? Aggregate agg)]}
  (s/assert (spec agg) agg))

(defn nilable-assert [agg]
  (when agg (assert agg))
  agg)
```

```clojre
(ns publicator.domain.user
  (:require
   [publicator.domain.protocols.aggregate :as aggregate]
   [clojure.spec.alpha :as s]))

(defrecord User [id login full-name password-digest]
  aggregate/Aggregate
  (spec [_] ::attrs))
```

Теперь можно проверять целостность пользователя следующим образом
`(aggregate/assert some-user)` или запросить специцикацию `(aggregate/spec some-user)`.
