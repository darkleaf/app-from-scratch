# Session

Сессия - сеанс работы с системой. Сохраняет данные между запросами.
Наши сценарии используют сессию, чтобы отслеживать вошел ли пользователь в систему.

Мы не собираемся делать сложные выборки по данным в сессии.
Для наших сценариев достаточно интерфейса ключ-значение.
Мы пока не знаем, как оно будет реализовано,
но уже знаем его интерфейс:

```clojure
(ns publicator.use-cases.abstractions.session
  (:refer-clojure :exclude [get set!]))

(defprotocol Session
  (-get [this k])
  (-set! [this k v]))

(declare ^:dynamic *session*)

(defn get [k]
  (-get *session* k))

(defn set! [k v]
  (-set! *session* k v))
```

Для тестирования будем использовать тривиальную реализацию, хранящую состояние в атоме:

```clojure
(ns publicator.use-cases.test.fakes.session
  (:require
   [publicator.use-cases.abstractions.session :as session]))

(deftype FakeSession [storage]
  session/Session
  (-get [_ k] (get @storage k))
  (-set! [_ k v] (swap! storage assoc k v)))

(defn binding-map []
  {#'session/*session* (FakeSession. (atom {}))})
```

Сама сессия дает только низкоуровневый интерфейс.
По этому сделаем службу для работы с сессией пользователя:

```clojure
(ns publicator.use-cases.services.user-session
  (:require
   [publicator.use-cases.abstractions.session :as session]
   [publicator.use-cases.abstractions.storage :as storage]))

(defn user-id []
  (session/get ::id))

(defn logged-in? []
  (boolean (user-id)))

(defn logged-out? []
  (not (logged-in?)))

(defn log-in! [user]
  (session/set! ::id (:id user)))

(defn log-out! []
  (session/set! ::id nil))

(defn user []
  (when-let [id (user-id)]
    (storage/tx-get-one id)))

(defn iuser [t]
  (when-let [id (user-id)]
    (storage/get-one t id)))
```

Абстракцию `storage` рассмотрим в следующем параграфе.
