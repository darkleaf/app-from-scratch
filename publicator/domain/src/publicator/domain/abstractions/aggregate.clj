(ns publicator.domain.abstractions.aggregate)

(defprotocol Aggregate
  (id [this])
  (valid? [this]))
