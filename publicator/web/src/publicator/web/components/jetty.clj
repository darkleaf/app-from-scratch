(ns publicator.web.components.jetty
  (:require
   [com.stuartsierra.component :as component]
   [ring.adapter.jetty :as jetty]
   [publicator.web.handler :as handler]))

(defn- wrap-binding [handler binding-map]
  (fn [req]
    (with-bindings binding-map
      (handler req))))

(defrecord Jetty [config server binding-map]
  component/Lifecycle
  (start [this]
    (if server
      this
      (assoc this :server
             (jetty/run-jetty
              (-> (handler/build config)
                  (wrap-binding (:val binding-map)))
              (assoc config :join? false)))))
  (stop [this]
    (if server
      (do
        (.stop server)
        (assoc this :server nil))
      this)))

(defn build [config]
  (Jetty. config nil nil))
