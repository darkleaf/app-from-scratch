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
