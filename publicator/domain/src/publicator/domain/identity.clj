(ns publicator.domain.identity
  (:require
   [publicator.domain.abstractions.aggregate :as aggregate]
   [clojure.spec.alpha :as s])
  (:import
   [clojure.lang Ref]))

(defn identity? [x]
  (and (instance? Ref x)
       (aggregate/aggregate? @x)
       (-> x get-validator some?)))

(s/def ::identity identity?)

(defn build [initial]
  (let [klass (class initial)
        id    (aggregate/id initial)]
    (ref initial :validator #(and (= klass (class %))
                                  (= id (aggregate/id %))
                                  (aggregate/valid? %)))))
