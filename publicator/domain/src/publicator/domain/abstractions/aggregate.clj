(ns publicator.domain.abstractions.aggregate
  (:require
   [clojure.spec.alpha :as s]))

(defprotocol Aggregate
  (id [this])
  (valid? [this]))

(s/def ::aggregate #(satisfies? Aggregate %))
