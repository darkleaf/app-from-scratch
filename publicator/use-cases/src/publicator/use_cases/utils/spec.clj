(ns publicator.use-cases.utils.spec
  (:require
   [clojure.spec.alpha :as s]))

;; https://groups.google.com/forum/#!topic/clojure/fti0eJdPQJ8
(defmacro only-keys [& {:keys [req req-un opt opt-un] :as args}]
  `(s/merge (s/keys ~@(apply concat (vec args)))
            (s/map-of ~(set (concat req
                                    (map (comp keyword name) req-un)
                                    opt
                                    (map (comp keyword name) opt-un)))
                      any?)))
