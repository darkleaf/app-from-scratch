(ns bench.bench
  (:require
   [criterium.core :as criterium]
   [clojure.template :as template]))

(defprotocol Proto
  (proto-method [this]))

(deftype A []
  Proto
  (proto-method [_] :ok))

(deftype B [])

(extend-type B
  Proto
  (proto-method [_] :ok))

(def c (reify
         Proto
         (proto-method [_] :ok)))

(deftype D [])
(defmulti multi-method class)
(defmethod multi-method D [_] :ok)

(defn bench []
  (template/do-template [method obj-expr]
                        (do
                          (prn '(method obj-expr))
                          (let [obj obj-expr]
                            (criterium/quick-bench (method obj)))
                          (print "\n\n\n"))
                        proto-method (->A)
                        proto-method (->B)
                        proto-method c
                        multi-method (->D)))

(comment
  (bench))
