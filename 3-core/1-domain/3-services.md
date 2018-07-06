# Services

Сервис - это действие не имеющее состояния, моделируется с помощью функций.
Сервисы слоя Domain(или Entities в терминологии Clean Architecture) представляют
только те действия, которые могут выполяться сотрудниками без компьютеров,
например с помощью картотеки. Т.е. сервисы этого слоя не могут делать рассылки, выборки в БД и т.п.

В сервисы удобно выносить функции для работы с ассоциациями:

```clojure
(ns publicator.domain.services.user-posts
  (:require
   [publicator.domain.aggregates.user :as user]
   [publicator.domain.aggregates.post :as post]
   [publicator.utils.ext :as ext]
   [clojure.spec.alpha :as s]))

(s/fdef add-post
  :args (s/cat :user ::user/user
               :post ::post/post)
  :ret ::user/user)

(defn add-post [user post]
  (update user :posts-ids conj (:id post)))


(s/fdef author?
  :args (s/cat :user (s/nilable ::user/user)
               :post (s/nilable ::post/post))
  :ret boolean?)

(defn author? [user post]
  (ext/in? (:posts-ids user) (:id post)))
```

В предыдущем случае сервисы работают только с состояниями.
Однако бывают случаи, когда сервис должен работать с идентичностями:

```clojure
(defn money-transfer [from to amount]
  (dosync
   (alter from update :account - amount)
   (alter to update :account + amount)))
```

Именно для этого случая идентичности описаны в слое Domain.
Однако, при разработке сервисов стоит отдавать предпочтение состояниям, а не идентичностям.
