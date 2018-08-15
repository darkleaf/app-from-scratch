(ns user
  (:require
   [com.stuartsierra.component :as component]
   [app.app :as app]))

(def component (app/build-jetty))

(defn start []
  (alter-var-root #'component component/start))

(defn stop []
  (alter-var-root #'component component/stop))
