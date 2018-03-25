(ns publicator.domain.identity-test
  (:require
   [publicator.domain.identity :as sut]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [clojure.test :as t]))

(defrecord Aggregate [id property]
  aggregate/Aggregate
  (id [_] id)
  (valid? [_] (some? property)))

(t/deftest identity-test
  (let [agg- (sut/build (->Aggregate 1 true))]
    (t/testing "validator"
      (t/is (thrown? RuntimeException
                     (dosync (alter agg- assoc :id 2))))
      (t/is (thrown? RuntimeException
                     (dosync (ref-set agg- nil))))
      (t/is (thrown? RuntimeException
                     (dosync (alter agg- assoc :property nil)))))))
