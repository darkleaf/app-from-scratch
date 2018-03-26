(ns publicator.domain.identity-test
  (:require
   [publicator.domain.identity :as sut]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [clojure.test :as t]))

(defrecord Aggregate [id property]
  aggregate/Aggregate
  (id [_] id)
  (valid? [_] (some? property)))

;; соглашение?
;; имя идентичности должно начинаться на i

(t/deftest identity-test
  (let [iagg (sut/build (->Aggregate 1 true))]
    (t/testing "predicate"
      (t/is (sut/identity? iagg)))
    (t/testing "validator"
      (t/is (thrown? RuntimeException
                     (dosync (alter iagg assoc :id 2))))
      (t/is (thrown? RuntimeException
                     (dosync (ref-set iagg nil))))
      (t/is (thrown? RuntimeException
                     (dosync (alter iagg assoc :property nil)))))))
