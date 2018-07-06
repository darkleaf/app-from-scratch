# Состояние сущностей

Сделаем одну сущность Person в отдельном проекте.

Есть несколько способов моделировать состояние сущности в clojure

1. Использовать мапы:

   ```clojure
   {:id   1
    :type :person
    :name "Alise"}
   ```

   Его минус в том, что реализовать полиморфизм для мап можно только с помощью мультиметодов.
   Также нам явно нужно указывать тип.
2. Использовать записи:

   ```clojrue
   (defrecord Person [id name])
   ```

   При этом мы можем использовать как протоколы, так и мультиметоды.
   Каждая сущность имеет свой тип(класс). При этом записи поддерживают интерфейс мап.
   И их объявление является документацией того, какие поля они имеют.
3. Модель [datomic](https://docs.datomic.com/cloud/whatis/data-model.html) или
   [datascript](https://github.com/tonsky/datascript).
   Не рассматриваем.

Для начала напишем тест на конструктор.
Конструктор - это обычная функция, возвращающая экземпляр `Person`.
Назовем наш конструктор `build`:

```clojure
(ns app.person
  (:require
   [clojure.test :as t]))

(declare build)
(declare person?)

(t/deftest build-test
  (let [params {:name "Alice"}
        person (build params)]
    (t/is (person? person))))
```

Добавим реализацию:

```clojure
(defrecord Person [id name])

(defn build [{:keys [name]}]
  (map->Person {:name name}))

(defn person? [x] (instance? Person x))
```

Отмечу, что наш конструктор не устанавливает идентификатор.
И наши сущности получаются неполноценными.

Напишем спецификацию на наш конструктор, чтобы проверять
корректность возващаемого значения.

```clojure
(ns app.person
  (:require
   [clojure.test :as t]
   [clojure.spec.alpha :as s]
   [orchestra.spec.test :as st]))

(s/def ::id pos-int?)
(s/def ::name string?)
(s/def ::person (s/keys :req-un [::id ::name]))

(defrecord Person [id name])

(s/fdef build
        :args (s/cat :params (s/keys :req-un [::name]))
        :ret ::person)

(defn build [{:keys [name]}]
  (map->Person {:name name}))

(defn person? [x] (instance? Person x))

;; подменяем функции на вариант, проверяющий спецификацию
(st/instrument)

(t/deftest build-test
  (let [params {:name "Alice"}
        person (build params)]
    (t/is (person? person))))
```

Ожидаемо наш тест не прошел, т.к. `build` не устанавливает
обязательное для персоны(`::person`) поле `id`.

Мы пока не знаем, как мы будем сохранять наши сущности, но уже
сейчас нам нужно генерировать идентификаторы.
Отложим принятие решения о конкретной реализации генератора и
объявим абстракцию генератора:

```clojure
(defprotocol IdGenerator
  (-generate-id [this]))

(declare ^:dynamic *id-generator*)

(s/fdef generate-id
        :ret ::id)

(defn generate-id []
  (-generate-id *id-generator*))
```

Т.е. наш генератор должен реализовывать протокол `IdGenerator`
и его экземляр должен храниться в динамической переменной
`*id-generator`.

Теперь мы можем использовать наш генератор в конструкторе:

```clojure
(defn build [{:keys [name]}]
  (map->Person {:id (generate-id)
                :name name}))
```

Для тестов напишем фейковую реализацию, храняющую данные в памяти.
Продробнее про фейки, моки и стабы можно посмотреть
[тут](https://cleancoders.com/episode/clean-code-episode-23-p1/show).

```clojure
(deftype FakeIdGenerator [counter]
  IdGenerator

  (-generate-id [_]
    (swap! counter inc)))

(defn build-fake-id-generator []
  (FakeIdGenerator. (atom 0)))
```

Теперь перед каждым тестом нужно создавать экземпляр герератора и
устанавливать его в динамическую переменную. Для этого воспользуемся
[фикстурами](https://clojuredocs.org/clojure.test/use-fixtures):

```clojure
(t/use-fixtures :each (fn [test]
                        (binding [*id-generator* (build-fake-id-generator)]
                          (test))))
```

Вуаля, наш тест проходит.

[Весь пример полностью](/4-core-domain/1-sources).

Очевидно, что мешать весь этот функционал в одином файле - плохая идея.
Не пугайтесь, я дам в дальнейшем пример структуры.

## Задание

1. Реализуйте Пользоватьля(User) с набором полей: id, login, full-name, password-digest, created-at.
   Параметры конструктора: login, full-name, password.
2. Функцию `(defn authenticated? [user password])` для проверки пароля.

Вам понадобится абстрактный PasswordHasher для получения `password-digest` и сверки пароля.
По аналогии нужно предусмотреть возможность задавать текущее время в тестах.

Проверьте себя:

+ https://github.com/darkleaf/publicator/blob/master/core/src/publicator/domain/abstractions/password_hasher.clj
+ https://github.com/darkleaf/publicator/blob/master/core/src/publicator/domain/test/fakes/password_hasher.clj
+ https://github.com/darkleaf/publicator/blob/master/core/src/publicator/domain/abstractions/instant.clj
