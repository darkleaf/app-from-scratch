# Persistence

Есть разные способы работы с базой данных.

## Active Record

Примеры для ruby и ActiveRecord из Ruby on Rails.

Есть проблема с отслеживанием изменений.

```ruby
user = User.first
user.skills << "codding"
user.save
```

В ruby массивы мутабельны, соответственно ORM не может
отследить добавление нового навыка и не сохранит это изменение.
Можно конечно сразу после загрузки делать deep copy,
и при сохранении сравнивать текущее состояние с изначальным,
но не всегда это возможно и приемлемо.

Доступна загрузка ассоциаций по требованию:

```ruby
user.posts
```

Однако вполне возможна рассинхронизация состояния
базы данных и программы:

```ruby
user.posts.length #=> 2
Post.create user: user, other_attr: ""
user.posts.length #=> 2
```

Вы можете загрузить одну и ту же сущность в разные объекты:

```ruby
o1 = User.first
o2 = User.find(o1.id)

o1 != o2
```

Ваши сущности зависят от фреймворка (см. Dependency Inversion Principle)

```
class User < ActiveRecord::Base
  has_many :posts
end
```

Разумеется есть и другие особенности, но нам достаточно приведенных.

В целом, для своей ниши это отличная ORM,
но в сложных проектах она начинает откровенно вредить.

## Commands & Queries

Наиболее простой механизм.
Объявляются функции,
которые только извлекают данные и только изменяют данные.
Задавая вопрос, не меняй ответ.

```clojure
(defn perform [params]
  ...
  (let [user (queries/get-user-by-id some-id)
        post (post/build params)
        post (assoc post :author-id (:id user))]
    (commands/put-post post)
    ...))
```

Тут уже нет изменяемых объектов, user и post - просто структуры данных вроде map или record.
Таким образом вы не зависите от деталей реализации.

Естественно, не получится ходить про связям `user.posts`.

Вы по прежнему можете отобразить одну сущность в несколько объектов в памяти:

```clojure
(let [user (queries/get-user-by-id 1)
      user (update user :achievements conj :fishing)
      ...
      author (gateway/get-user-by-id 1)
      author (update author :achievements conj :writing)]
  (commands/put-user user)
  ...
  (commands/put-user author))
```

В данном примере мы теряем часть изменений, а именно изменения "автора"
перетрут изменения пользователя.

Если используются транзакции,
и эти транзакции занимают некоторое время,
то при большом потоке изменений будут возникать
дедлоки и придется вручную расставлять блокировки.

Этот подход хорошо работает в функциональных языках и
просто языках без развитой инфраструткуры ORM.

## Data Mapper




## Datomic / Datascript
