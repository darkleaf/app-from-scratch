(ns user
  (:require
   [com.stuartsierra.component :as component]
   [publicator.web.components.jetty :as jetty]
   [publicator.use-cases.test.fakes.storage :as storage]
   [publicator.use-cases.test.fakes.user-queries :as user-q]
   [publicator.use-cases.test.fakes.post-queries :as post-q]))

(defrecord BindingMap [val]
  clojure.lang.IDeref
  (deref [_] val)
  component/Lifecycle
  (start [this]
    (let [db (storage/build-db)]
      (assoc this :val
             (merge (storage/binding-map db)
                    (user-q/binding-map db)
                    (post-q/binding-map db)))))
  (stop [this] this))

(def system (component/system-map
             :binding-map (->BindingMap nil)
             :jetty (component/using (jetty/build {:port 4445})
                                     [:binding-map])))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system component/stop))
