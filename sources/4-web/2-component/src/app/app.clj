(ns app.app
  (:require
   [clojure.pprint :as pp]
   [com.stuartsierra.component :as component]
   [ring.adapter.jetty :as jetty]))

(defn handler [req]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (with-out-str (pp/pprint req))})

(defrecord Jetty [val]
  component/Lifecycle
  (start [this]
    (if val
      this
      (assoc this :val
             (jetty/run-jetty #'handler
                              {:port 4445
                               :join? false}))))
  (stop [this]
    (if-not val
      this
      (do
        (.stop val)
        (assoc this :val nil)))))

(defn build-jetty []
  (->Jetty nil))
