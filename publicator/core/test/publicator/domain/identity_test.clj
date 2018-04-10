(ns publicator.domain.identity-test
  (:require
   [publicator.domain.identity :as sut]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [publicator.utils.fixtures :as utils.fixtures]
   [clojure.spec.alpha :as s]
   [clojure.test :as t]))

(t/use-fixtures :once utils.fixtures/instrument)

(defrecord Aggregate [id property]
  aggregate/Aggregate
  (id [_] id)
  (spec [_] (fn [_] (some? property))))

;; соглашение?
;; имя идентичности должно начинаться на i

(t/deftest identity-test
  (let [iagg (sut/build (->Aggregate 1 true))]
    (t/testing "spec"
      (t/is (s/valid? ::sut/identity iagg))
      (t/is (not (s/valid? ::sut/identity (ref nil)))))
    (t/testing "validator"
      (t/is (thrown? RuntimeException
                     (dosync (alter iagg assoc :id 2))))
      (t/is (thrown? RuntimeException
                     (dosync (ref-set iagg nil))))
      (t/is (thrown? RuntimeException
                     (dosync (alter iagg assoc :property nil)))))))
