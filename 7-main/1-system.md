# System

```clojure
(ns publicator.main.core
  (:require
   [com.stuartsierra.component :as component]
   [signal.handler :as signal]
   [publicator.web.components.jetty :as jetty]
   [publicator.web.components.handler :as handler]
   [publicator.persistence.components.data-source :as data-source]
   [publicator.persistence.components.migration :as migration]
   [publicator.persistence.utils.env :as env]
   [publicator.main.binding-map :as binding-map]))

(defn http-opts []
  {:host "0.0.0.0"
   :port (bigint (System/getenv "PORT"))})

(defn -main [& _]
  (let [system (component/system-map
                :data-source (data-source/build (env/data-source-opts "DATABASE_URL"))
                :migration (component/using (migration/build) [:data-source])
                :binding-map (component/using (binding-map/build) [:data-source])
                :handler (component/using (handler/build) [:binding-map])
                :jetty (component/using (jetty/build (http-opts)) [:binding-map :handler]))
        system (component/start system)]
    (signal/with-handler :term
      (prn "caught SIGTERM, quitting.")
      (component/stop system)
      (System/exit 0))))
```

```clojure
(ns publicator.main.binding-map
  (:require
   [com.stuartsierra.component :as component]
   [publicator.persistence.storage :as storage]
   [publicator.persistence.storage.user-mapper :as user-mapper]
   [publicator.persistence.storage.post-mapper :as post-mapper]
   [publicator.persistence.user-queries :as user-q]
   [publicator.persistence.post-queries :as post-q]
   [publicator.persistence.id-generator :as id-generator]
   [publicator.crypto.password-hasher :as password-hasher]))

(defrecord BindingMap [data-source val]
  component/Lifecycle
  (start [this]
    (let [data-source (:val data-source)
          mappers     (merge
                       (post-mapper/mapper)
                       (user-mapper/mapper))
          binding-map (merge
                       (storage/binding-map data-source mappers)
                       (user-q/binding-map data-source)
                       (post-q/binding-map data-source)
                       (password-hasher/binding-map)
                       (id-generator/binding-map data-source))]
      (assoc this :val binding-map)))
  (stop [this]
    (assoc this :val nil)))

(defn build []
  (BindingMap. nil nil))
```
