# Абстракции хранилища

Я буду ссылаться на различные паттерны проектирования, однако не существует канонической
реализации того или иного паттерна, и все, что будет описано далее, следует воспринимать
как вариацию на тему.

## Gateway

Самый простой способ работать с базой данных в clojure - использовать тривиальную
реализацию паттерна [Data Mapper](https://www.martinfowler.com/eaaCatalog/dataMapper.html).

Она похожа на [Table Data Gateway](https://www.martinfowler.com/eaaCatalog/tableDataGateway.html).
Только в нашем случае сущности не будут зависить от шлюза,
шлюз будет оперировать агрегатами, а не полями агрегата,
и шлюз будет работать с несколькими таблицами,
ведь агрегат может храниться в нескольких таблицах.


```clojure
(ns publicator.interactors.abstractions.gateway)

(defprotocol Gateway
  (-get-many [this ids])
  (-put-many [this aggregates])
  (-delete-many [this aggregates]))

(declare ^:dynamic *gateway*)

(defn get-many [ids]
  (-get-many *gateway* ids))
(defn put-many [aggregates]
  (-put-many *gateway* aggregates))
(defn delete-many [aggregates]
  (-delete-many *gateway* aggregates))

(defn get-one [id] ...)
(defn put-one [aggregate] ...)
(defn delete-one [aggreate] ...)
```

```clojure
(ns publicator.interactors.some-interactor
  (:require
   [publicator.domain.post :as post]
   [publicator.interactors.abstractions.gateway :as gateway]))

...

(defn perform [params]
  ...
  (let [user (gateway/get-one some-id)
        post (post/build params)
        post (assoc post :author-id (:id user))]
    (gateway/put-one post)
    ...))
```

Т.к. при создании сущности еще до сохранения в базу она получает идентификатор,
то методы `insert` и `update` сливаются в `put`.

В нашем приложении корни агрегатов имеют глобально уникальные идентификаторы.
Если бы это было не так, то протокол был бы таким:

```clojure
(defprotocol Gateway
  (-get-many [this klass ids])
  (-put-many [this aggregates])
  (-delete-many [this aggregates]))

...

(let [user (gateway/get-one User some-id)
      post (gateway/get-one Post some-id)]
  ...)
```

Или для каждого класса агрегата нужен был бы свой шлюз.

При желании можно добавить поддержку транзакций:

```clojure
(defprotocol Gateway
  (-get-many [this tx ids])
  (-put-many [this tx aggregates])
  (-delete-many [this tx aggregates])
  (-wrap-tx [this body]))

(defmacro with-tx [tx-name & body]
  `(-wrap-tx *gateway* (fn [~tx-name] ~@body)))

...

(with-tx t
  (gateway/put-one t user)
  (gateway/put-one t post))
```

Шлюз подходит для простых приложений. Он требует дополнительного внимания от разработчика.

Он не отображает идентификаторы на ссылки объектов в памяти.
В примере ниже в памяти программы мы одновременно оперируем одним и тем же
пользователем, но с разными состояниями. В итоге мы потеряем часть изменений:

```clojure
(let [user (gateway/get-one 1)
      user (update user :achievements conj :fishing)
      ...
      author (gateway/get-one 1)
      author (update author :achievements conj :writing)]
  (gateway/put user)
  (gateway/put author))
```

Если мы не изменили агрегат, то все равно будет запрос к базе данных:

```clojure
(let [user (gateway/get-one 1)]
  (gateway/put user))
```

В сложных сценариях вы можете забыть сделать сохранение.
Забыли сохранить пользователя:

```clojure
(let [user (gateway/get-one 1)
      ...
      user (update user :achievements conj :writing)
      ...
      post (gateway/get-one 2)
      post (assoc post :title "New title")]
  (gateway/put post))
```

Если приложение обрабатывает большой поток транзакций, то могут возникать dead-locks.
Придется вручную расставлять блокировки.

## Data Mapper

Для нашего приложения подходит Шлюз, но стоит показать решение, которое лишено
выше перечисленных проблем. Оно базируется на паттернах
[Data mapper](https://www.martinfowler.com/eaaCatalog/dataMapper.html),
[Identity map](https://www.martinfowler.com/eaaCatalog/identityMap.html) и
[Unit of work](https://www.martinfowler.com/eaaCatalog/unitOfWork.html).

Identity map задает соответствие между идентификатором сущности и ссылкой на объект.
Таким образом, повторно запрашивая сущность из хранилища вы получите тот же самый объект.

Unit of work отслеживает изменения объектов в памяти и автоматически фиксирует изменения.
Как правило, для этого используются оптимистические блокировки.
Т.е. мы разрываем связь между бизнес-транзакцией и транзакцией базы данных:

+ вычитываем из базы состояние агрегата и его версию
+ производим манипуляции с объектом
+ открываем транзакцию
+ устанавливаем блокировку на извлеченные агрегаты и извлекаем текущии версии
+ если версии не изменились, то пачкой записываем все изменения
+ если версии отличаются, то повторяем бизнес-транзакцию
+ фиксируем тразакцию

При таком подходе бизнес транзакция может вычитывать данные в любом порядке не боясь поймать
deadlock. Бизнес транзакция может выполняться очень долго и не мешать другим.

### Модель времени

Пережде чем продолжить, нужно понять модель времени Clojure.
Rich Hickey подробно рассказал о ней в своем докладе
[Are We There Yet?](https://github.com/matthiasn/talk-transcripts/blob/master/Hickey_Rich/AreWeThereYet.md).
Я приведу пару слайдов, которые важны для понимания текущего раздела.

<img src="img/identity_state_value.jpg" alt="Identity, state, value">
<img src="img/time_model.jpg" alt="Identity, state, value">

Смоделируем реку Волгу.
В нашей модели время будет дискретным.
В каждый квант времени реку можно представить как некое значение,
например расположение и тип атомов вещества в данный момент.
Это значение должно быть неизменяемым, т.к. нет способа вернуться в прошлое.
Процессы, происходящие с Волгой моделируются как чистая функция,
принимающая значение для предыдущего кванта и возвращающая значение
для текущего.
Люди ассоциируют череду этих значений с названием Волга.

Фактически речь идет о том, что есть неизменяемое значение и
контейнер, позволяющий контроллируемым образом изменять свое содежимое.

Подробнее можно прочитать в [Values and Change: Clojure’s approach to Identity and State](https://clojure.org/about/state).

### Моделирование идентичности агрегата

```clojure
(ns publicator.interactors.abstractions.storage
  (:refer-clojure :exclude [swap!])
  (:require
   [medley.core :as medley]
   [publicator.domain.protocols.aggregate :as aggregate]))

(defprotocol AggregateBox
  (-set! [this new])
  (-id [this])
  (-version [this]))

(defn box? [x]
  (and
   (satisfies? AggregateBox x)
   (instance? clojure.lang.IDeref x)))

(defn id [box]
  {:pre [(box? box)]}
  (-id box))

(defn version [box]
  {:pre [(box? box)]}
  (-version box))

(defn destroy! [box]
  {:pre [(box? box)]}
  (-set! box nil))

(defn swap! [box f & args]
  {:pre [(box? box)]}
  (let [old (aggregate/nilable-assert @box)
        new (aggregate/nilable-assert (apply f old args))]
    (assert (or (nil? old)
                (nil? new)
                (and (= (:id old) (:id new))
                     (= (class old) (class new)))))
    (-set! box new)
    new))
```

За идентичность агрегата отвечает тип, реализующий протокол `AgregateBox`
и интерфейс `clojure.lang.IDeref`.

+ `clojure.lang.IDeref`
  + `deref`, `@` - получеие текущего состояния
+ `AgregateBox`
  + `id` - получение идентификатора агрегата, в том числе удаленого
  + `version` - получение версии агрегата, в том числе удаленого
  + `swap!` - изменение агрегата с проверками целостности
  + `destroy!` - удаляет агрегат, устанавливает его состояние в `nil`

В разделе "Предметная область" я упоминал, что агрегат должен поддерживать свою целостность,
для этого мы добавили спецификацию. Функция `swap!` при каждом изменении проверяет
соответситве состояния спецификации. Проверки раелизованы с помощью макроса `assert`
и могут быть удалены в production окружении, т.е. не повлияют на производительность.

### Моделирование хранишища

Продолжение предыдущего листинга:

```clojure

(defprotocol Storage
  (-wrap-tx [this body]))

(defprotocol Transaction
  (-get-many [this ids])
  (-create [this state]))

(declare ^:dynamic *storage*)

(defmacro with-tx
  "Note that body forms may be called multiple times,
   and thus should be free of side effects."
  [tx-name & body-forms-free-of-side-effects]
  `(-wrap-tx *storage* (fn [~tx-name] ~@body-forms-free-of-side-effects)))

(defmacro ^:private assert-idempotence [form message]
  `(let [first-result# ~form]
     (assert (= first-result# ~form) ~message)
     first-result#))

(defn get-many [tx ids]
  {:pre [(every? some? ids)]
   :post [(map? %)
          (<= (count %) (count ids))
          (every? box? (vals %))]}
  (assert-idempotence (-get-many tx ids) "Identity Map isn't implemented!"))

(defn get-one [tx id]
  {:post [((some-fn nil? box?) %)]}
  (let [res (get-many tx [id])]
    (get res id)))

(defn create [tx state]
  {:post [(box? %)]}
  (-create tx state))

;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

(defn tx-get-one [id]
  (with-tx tx
    (when-let [x (get-one tx id)]
      @x)))

(defn tx-get-many [ids]
  (with-tx tx
    (->> ids
         (get-many tx)
         (medley/map-vals deref))))

(defn tx-create [state]
  (with-tx t
    @(create t state)))

(defn tx-swap! [id f & args]
  (with-tx t
    (when-let [x (get-one t id)]
      (apply swap! x f args))))

(defn tx-destroy! [id]
  (with-tx t
    (let [x (get-one t id)]
      (destroy! x))))
```

Принципиальные моменты:

+ Тело транзакции можно быть выполенено несколько раз. Т.к. используются оптимистические
  блокировки, то в случае обнаружения конфликтов, транзакцию нужно перезапустить.
+ Абстракция проверяет наличие Identity Map в реализации.

```clojure
(t/deftest create
  (let [entity (storage/tx-create (build-test-entity))]
    (t/is (some? entity))
    (t/is (some? (storage/tx-get-one (:id entity))))))

(t/deftest swap
  (let [entity (storage/tx-create (build-test-entity))
        _      (storage/tx-swap! (:id entity) update :counter inc)
        entity (storage/tx-get-one (:id entity))]
    (t/is (= 1 (:counter entity)))))

(t/deftest destroy
  (let [entity (storage/tx-create (build-test-entity))
        _      (storage/tx-destroy! (:id entity))
        entity (storage/tx-get-one (:id entity))]
      (t/is (nil? entity))))

(t/deftest nop
  (let [id (storage/with-tx t
             (let [entity (storage/create t (build-test-entity))]
               (storage/destroy! entity)
               (storage/id entity)))]
    (t/is (nil? (storage/tx-get-one id)))))

(t/deftest identity-map-persisted
  (let [id (:id (storage/tx-create (build-test-entity)))]
    (storage/with-tx t
      (let [x (storage/get-one t id)
            y (storage/get-one t id)]
        (t/is (identical? x y))))))

(t/deftest identity-map-in-memory
  (storage/with-tx t
    (let [x (storage/create t (build-test-entity))
          y (storage/get-one t (storage/id x))]
      (t/is (identical? x y)))))

(t/deftest identity-map-swap
  (storage/with-tx t
    (let [x (storage/create t (build-test-entity))
          y (storage/get-one t (storage/id x))
          _ (storage/swap! x update :counter inc)]
      (t/is (= 1 (:counter @x) (:counter @y))))))
```

Чтоыб создать агрегат, нужно передать функции `storage/create`
транзакцию и состояние агрегата. Таким обаразом мы зарегистрируем новый агрегат в
Unit of Work и Identity map.

Для изменения агрегата не требуется объект транзакции, следовательно `box` можно легко
передавать в функции:

```clojure
(storage/with-tx t
  (let [entity (storage/get-one t 1)]
    (action-1 entity)
    (action-2 entity)))
```

Решение для N+1 запросов:

```clojure
(let [ids (fetch-some-posts-ids)]
  (storage/with-tx t
    (let [posts-by-id (storage/get-many t ids) ;; first storage access
          authors-ids (->> posts-by-id
                           (vals)
                           (map deref)
                           (map :author-id))
          _           (storage/get-many t authors-ids)] ;; second storage access
      (doseq [post (vals posts-by-id)
              :let [author (storage/get-one t (:author-id @post))]] ;; identiity map cache
        (prn @author)))))
```

### Запросы

Эта абстракция позволяет извлекать агрегаты только по их id.
Это сделано намерено.

**Проблема 1**. Представим ситуацию:

+ начали бизнес-транзакцию
+ извлекли пользователя по его id
+ сменили ему город проживания с "Сакнт-Петербург" на "Москва"
+ выбираем пользователей, проживающих в городе "Москва"

Должен ли наш пользователь попасть в эту выборку?
Бизнес транзакция не соответствует транзакции хранилища,
и хранилище не знает о изменении нашего ползователя.
Наверное, можно каким-то хитрым образом подправлять результаты выборки,
но оно того не стоит.

**Проблема 2**. На каком языке описывать запросы?
В наша абстракция протекает до конкретной реализации SQL в конкретной базе данных?
Сделать универсальный ограниченный язык запросов?

**Решение**. Мы разделим абстракции. Есть абстракция хранилища,
она поддерживает поиск по id, реализует Identity Map и Unit of Work.
А есть абстракция запроса, который возвращает некие данные.
Например, нам нужно найти пользователя по его email,
для этого мы объявляем соответствующую абстракцию:

```clojure
(ns publicator.interactors.abstractions.user-queries
  (:require
   [publicator.domain.user])
  (:import
   [publicator.domain.user User]))

(defprotocol GetByLogin
  (-get-by-login [this login]))

(declare ^:dynamic *get-by-login*)

(defn get-by-login [login]
  {:post [(or (nil? %)
              (instance? User %))]}
  (-get-by-login *get-by-login* login))
```

Отмечу, что `get-by-login` возвращает не идентичность пользователя
(рализацию протокола AggregateBox), а его стостояние(записть User).

Если нам нужно изменить этого пользователя, то нужно воспрользоваться абстракцией
хранилища, и снова найти пользователя, только теперь уже по его идентификатору
полученному ранее.

Запросы могут возвращать все что угодно, любые структуры данных.

Повторно извлекать теже самые данные(в некоторых случаях) может показаться странным,
но это не так. Предположим у нас есть несколько баз: мастер и ассинхронная реплика,
или мастер и поисковый движок вроде ElasticSearch. Данные в реплике отстают от мастера.
Мастер хранит актуальные данные. Так вот, абстракция хранилища работает с мастером,
а абстракции запросов работают с репликами. Повторно запрашивая данные мы уходим от проблем,
возникающих при нарушении согласованности баз.

### Команды

Абстракция хранилища - не единственный способ изменять данные.
Наверняка, может случиться ситуация с которой не справится эта абстракция.
Но вы всегда можете объявить абстракцию команды.

### Поддельная реализация

Для разработки нужна какая-то реализация абстракций.
Нам не нужны ACID гарантии:
мы не беспокоемся о сохранности данных,
мы не работаем в несколько потоков.
Поэтому наше фейковое хранилище максимально просто, хранит данные в памяти и
не имеет изоляции транзакций:

```clojure
(ns publicator.fake.storage
  "Storage with fake transactions.
   No isolation, no rollback."
  (:require
   [publicator.interactors.abstractions.storage :as storage]))

(deftype AggregateBox [volatile id]
  clojure.lang.IDeref
  (deref [_] @volatile)

  storage/AggregateBox
  (-set! [_ new] (vreset! volatile new))
  (-id [_] id)
  (-version [_] nil))

(defn- build-box [state id]
  (AggregateBox. (volatile! state) id))

(deftype Transaction [db]
  storage/Transaction
  (-get-many [_ ids]
    (select-keys @db ids))

  (-create [_ state]
    (let [id  (:id state)
          box (build-box state id)]
      (swap! db assoc id box)
      box)))

(deftype Storage [db]
  storage/Storage
  (-wrap-tx [_ body]
    (let [t (Transaction. db)]
      (body t))))

(defn build-db []
  (atom {}))

(defn binding-map [db]
  {#'storage/*storage* (->Storage db)})
```

[`volatile!`](https://clojuredocs.org/clojure.core/volatile%21) это аналог Atom,
только без контроля параллельного доступа.
Этот ссылочнй объект имеет тривиальную [реализацию](https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/Volatile.java).

Запросы реализуются схожим образом:

```clojure
(ns publicator.fake.user-queries
  (:require
   [publicator.interactors.abstractions.user-queries :as user-q])
  (:import
   [publicator.domain.user User]))

(deftype GetByLogin [db]
  user-q/GetByLogin
  (-get-by-login [_ login]
    (->> db
         (deref)
         (vals)
         (map deref)
         (filter #(instance? User %))
         (filter #(= login (:login %)))
         (first))))

(defn binding-map [db]
  {#'user-q/*get-by-login* (->GetByLogin db)})
```

Отмечу, что они могут использовать одну и ту же базу данных, которая устанавливается через
функцию `binding-map`. Объект базы данных создается фукнцией `publicator.fake.storage.build-db`
