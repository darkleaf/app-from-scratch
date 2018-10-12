# System

Ранее мы познакомились к
[компонентами](https://github.com/darkleaf/app-from-scratch/blob/master/4-web/2-component.md).
Я упоминал, что компоненты объединяются в систему. Подробнее о
[системах](https://github.com/stuartsierra/component#systems).

Мы уже сделали достаточно, чтобы собрать полноценную систему и провести демонстрацию.

```clojure
(defn build []
  (component/system-map
   :binding-map (->BindingMap nil)
   :seed        (component/using (->Seed nil)               [:binding-map])
   :handler     (component/using (handler/build)            [:binding-map])
   :jetty       (component/using (jetty/build {:port 4445}) [:binding-map :handler])))
```

Наша система состоит из 4х компонентов:

+ `binding-map` - содержит реализации абстракций вместе с их состоянием
+ `seed` - компонент без состояния, который добавляет в хранилище начальные данные
+ `handler` - компонент без состояния, оборачивающий ring handler
+ `jetty` - веб-сервер

Эта система используется для разработки, но вы можете развертывать ее на тестовых серверах,
для демонстрации промежуточного результата.
Эта система использует фейки, хранящие данные в памяти, поэтому вы можете легко поднимать
тестовое окружение на каждую фичу.

Файлы для разработки хранятся в директории
[web/dev](https://github.com/darkleaf/publicator/tree/master/web/dev)
.

```
(ns system
  (:require
   [com.stuartsierra.component :as component]
   [publicator.web.components.jetty :as jetty]
   [publicator.web.components.handler :as handler]
   [publicator.use-cases.test.factories :as factories]
   [publicator.use-cases.test.fakes.storage :as storage]
   [publicator.use-cases.test.fakes.user-queries :as user-q]
   [publicator.use-cases.test.fakes.post-queries :as post-q]
   [publicator.domain.test.fakes.id-generator :as id-generator]
   [publicator.domain.test.fakes.password-hasher :as password-hasher]))

(defrecord BindingMap [val]
  component/Lifecycle
  (start [this]
    (let [db (storage/build-db)]
      (assoc this :val
             (merge (storage/binding-map db)
                    (user-q/binding-map db)
                    (post-q/binding-map db)
                    (id-generator/binding-map)
                    (password-hasher/binding-map)))))
  (stop [this] this))

(defrecord Seed [binding-map]
  component/Lifecycle
  (start [this]
    (with-bindings (:val binding-map)
      (let [post1 (factories/create-post)
            user1 (factories/create-user {:login "user1"
                                          :password "12345678"
                                          :full-name "User1"
                                          :posts-ids #{(:id post1)}})
            post2 (factories/create-post)
            user2 (factories/create-user {:login "user2"
                                          :password "12345678"
                                          :full-name "User2"
                                          :posts-ids #{(:id post2)}})]))
    this)
  (stop [this]
    this))

(defn build []
  (component/system-map
   :binding-map (->BindingMap nil)
   :seed (component/using (->Seed nil)
                          [:binding-map])
   :handler (component/using (handler/build)
                             [:binding-map])
   :jetty (component/using (jetty/build {:port 4445})
                           [:binding-map :handler])))
```

```clojure
(ns user
  (:require
   [com.stuartsierra.component :as component]
   [system]))

(def system (system/build))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system component/stop))
```
