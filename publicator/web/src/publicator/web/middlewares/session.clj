(ns publicator.web.middlewares.session
  (:require
   [ring.middleware.session :as ring.session]
   [publicator.use-cases.abstractions.session :as session]))

(deftype Session [storage]
  session/Session
  (-get [_ k] (get @storage k))
  (-set! [_ k v] (swap! storage assoc k v)))

(defn- wrap-binding [handler]
  (fn [req]
    (let [storage (atom (:session req))
          resp    (binding [session/*session* (Session. storage)]
                    (handler req))
          resp    (assoc resp :session/key (:session/key req))
          resp    (assoc resp :session @storage)]
      resp)))

(defn wrap [handler options]
  (-> handler
      wrap-binding
      (ring.session/wrap-session options)))
