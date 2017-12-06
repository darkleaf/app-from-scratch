# Абстракции хранилища

## Table Data Gateway

Самый простой способ работать с базой данных в clojure - реализовать паттерн
[Table Data Gateway](https://www.martinfowler.com/eaaCatalog/tableDataGateway.html).

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

[Data mapper](https://www.martinfowler.com/eaaCatalog/dataMapper.html)


Как правило, фреймворки реализуют патерн Active record и предоставляют базовый класс для наших сущностей.
Такой подход идет вразрез с принципом инверсии зависимости.
Получается, что наша сущность зависит от деталей реализации.

Есть различные способы соблюсти этот принцип и использовать Active record.
Например, реализовать сущность как класс с абстрактными методами доступа к данным(find, save, udate), конкретные инстансы получать с помощью абстрактной фабрики.
Уровнями ниже должен быть объявлен класс, потомок нашей сущности, реализующий методы доступа к данным.

Считается, что более сложным является паттерн Data mapper.
При таком подходе наши сущности ничего не знают про persistence слой.
Это просто обычные объекты, в завсисимости от языка их называют
POJO (Plain Old Java Object).

При реализации Data mapper помогают шаблоны [Единица работы(Unit of work)](https://www.martinfowler.com/eaaCatalog/unitOfWork.html) и [Identity map](https://www.martinfowler.com/eaaCatalog/identityMap.html).

Unit of work отслеживает изменения объектов в памяти.
В конце бизнес транзакции происходит сохранение созданных/удаленных/измененных объектов.

Identity map задает соответствие между идентификатором сущности и ссылкой на объект.
Таким образом, повторно запрашивая сущность из хранилища вы получите тот же самый объект.





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
