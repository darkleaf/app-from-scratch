(ns publicator.use-cases.test.fakes.storage-test
  (:require
   [publicator.use-cases.test.fakes.storage :as sut]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [clojure.test :as t]))

(t/use-fixtures
  :each
  (fn [f]
    (with-bindings (sut/binding-map (sut/build-db))
      (f))))

(defrecord Test [counter]
  aggregate/Aggregate
  (id [_] 42)
  (spec [_] any?))

(t/deftest concurrency
  (let [test (storage/tx-create (->Test 0))
        id   (aggregate/id test)
        n    100
        _    (->> (range n)
                  (map (fn [_] (future (storage/tx-alter id update :counter inc))))
                  (map deref)
                  (doall))
        test (storage/tx-get-one id)]
    (t/is (= n (:counter test)))))
