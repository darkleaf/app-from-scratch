(ns publicator.domain.abstractions.aggregate)

(defprotocol Aggregate
  (id [this])
  (valid? [this]))

(defn aggregate? [x]
  (satisfies? Aggregate x))
