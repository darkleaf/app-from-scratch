(ns darkleaf.cider
  (:require [clojure.tools.nrepl.server :as nrepl-server]))

(defn- stop [])
(defn- start [])

(defn- inject-start-stop []
  (let [user (find-ns 'user)]
    (when-not (ns-resolve user 'stop)
      (intern 'user 'stop stop))
    (when-not (ns-resolve user 'start)
      (intern 'user 'start start))))

(inject-start-stop)

(defn- nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defn -main [& {:strs [port host]
                :or {port 4444, host "0.0.0.0"}}]
  (prn (nrepl-server/start-server :port (bigint port)
                                  :bind host
                                  :handler (nrepl-handler))))
