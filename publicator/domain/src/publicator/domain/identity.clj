(ns publicator.domain.identity
  (:require
   [publicator.domain.abstractions.aggregate :as aggregate])
  (:import
   [clojure.lang Ref]))

(defn build [initial]
  (let [klass (class initial)
        id    (aggregate/id initial)]
    (ref initial :validator #(and (= klass (class %))
                                  (= id (aggregate/id %))
                                  (aggregate/valid? %)))))

(defn identity? [x]
  (and (instance? Ref x)
       (-> x get-validator some?)))
