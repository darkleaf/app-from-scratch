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

Unit of work отслеживает изменения объектов в памяти и автоматически фиксирует изменения
в конце бизнес-транзакции.
Причем, можно использовать оптимистические блокировки.
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

Будем считать время дискретным.
Представим реку, например Волгу.
В каждый квант времени ее можно представить как некое значение.
Это значение должно быть неизменяемым, т.к. нет способа вернуться в прошлое.
Процессы, происходящие с Волгой моделируются как чистая функция,
принимающая значение для предыдущего кванта и возвращающая значение
для текущего.
Люди ассоциируют череду этих значений как одно цело целое - Волгу.
Это и есть идентичность.







## Хранилище

Хранилище - это нечто, что сохраняет состояние сущностей.
Я очень долго думал и искал различные варианты того, как работать с хранилищем.


Модель предметной области находится в модулях самого высокого уровня.
Сущности не могут зависеть от

то сущности не должны зависеть
Сущности не должны зависеть от абстракции


Существуют

Для работы с сущностями нужна абстракция со следующими свойствами:

+ извлечение сущности по ее идентификатору
+ повторное извлечение сущности по идентификатору
  возвращает тот же объект что и предыдущее извлечение
  (Identity map)
+ сущности ничего не знают про хранилище,
  мы работаем с сущностаями как с простыми структурами данных
  (Datamapper)
+ поддержка ACID транзакций


Есть несколько требований


Хранилище нужно для сохраниения состояния сущностей.
Это может быть mysql, postgres, mongodb, elasticsearch, redis, файл, оперативная память.
