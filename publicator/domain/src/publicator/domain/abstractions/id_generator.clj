(ns publicator.domain.abstractions.id-generator
  (:require
   [clojure.spec.alpha :as s]))

(defprotocol IdGenerator
  (-generate [this]))

(declare ^:dynamic *id-generator*)

(s/def ::id some?)

(s/fdef generate
        :ret ::id)

(defn generate []
  (-generate *id-generator*))
