(ns publicator.use-cases.test.fakes.session
  (:require
   [publicator.use-cases.abstractions.session :as session]))

(deftype FakeSession [storage]
  session/Session
  (-get [_ k] (get @storage k))
  (-set! [_ k v] (swap! storage assoc k v)))

(defn binding-map []
  {#'session/*session* (FakeSession. (atom {}))})
