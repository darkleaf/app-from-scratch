# Сессия

Ранее мы рассматривали [абстракцию сессии](/03-core/02-use-cases/02-session)
теперь займемся её реализацией.

Ring добавляет поддержку http сессии с помощью middleware `ring.middleware.session/wrap-session`.
В запросе появляется ключ `:session`, который и хранит данные сессии.
По умолчанию сессия хранится в памяти процесса, есть возможность хранить ее в cookie или
написать свою реализацию.

Воспользуемся http сессией и реализуем нашу абстракцию:

```clojure
(ns publicator.web.middlewares.session
  (:require
   [publicator.use-cases.abstractions.session :as session]))

(deftype Session [storage]
  session/Session
  (-get [_ k] (get @storage k))
  (-set! [_ k v] (swap! storage assoc k v)))

(defn wrap-session [handler]
  (fn [req]
    (let [storage (atom (get-in req [:session ::storage]))
          resp    (binding [session/*session* (Session. storage)]
                    (handler req))]
      (-> resp
          (assoc :session/key (:session/key req))
          (assoc :session (:session req))
          (assoc-in [:session ::storage] @storage)))))
```
