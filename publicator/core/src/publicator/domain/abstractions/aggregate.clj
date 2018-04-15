(ns publicator.domain.abstractions.aggregate
  (:require
   [clojure.spec.alpha :as s]))

(defprotocol Aggregate
  (id [this])
  (spec [this])
  (wrap-update [this]))

(s/def ::aggregate #(satisfies? Aggregate %))

(s/fdef wrap-update
        :ret ::aggregate)
