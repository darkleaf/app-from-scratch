(ns publicator.use-cases.test.fakes.storage-test
  (:require
   [publicator.use-cases.test.fakes.storage :as sut]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [publicator.domain.identity :as identity]
   [publicator.utils.fixtures :as utils.fixtures]
   [clojure.test :as t]))

(t/use-fixtures :once utils.fixtures/instrument)

(t/use-fixtures
  :each
  (fn [f]
    (with-bindings (sut/binding-map (sut/build-db))
      (f))))

(defrecord Test [counter]
  aggregate/Aggregate
  (id [_] 42)
  (spec [_] any?)
  (wrap-update [this] this))

(t/deftest create
  (let [test (storage/tx-create (->Test 0))
        id (aggregate/id test)]
    (t/is (some? test))
    (t/is (some? (storage/tx-get-one id)))))

(t/deftest swap
  (let [test (storage/tx-create (->Test 0))
        id   (aggregate/id test)
        _    (storage/tx-alter id update :counter inc)
        test (storage/tx-get-one id)]
    (t/is (= 1 (:counter test)))))

(t/deftest identity-map-persisted
  (let [test (storage/tx-create (->Test 0))
        id   (aggregate/id test)]
    (storage/with-tx t
      (let [x (storage/get-one t id)
            y (storage/get-one t id)]
        (t/is (identical? x y))))))

(t/deftest identity-map-in-memory
  (storage/with-tx t
    (let [x (storage/create t (->Test 0))
          y (storage/get-one t (aggregate/id @x))]
      (t/is (identical? x y)))))

(t/deftest identity-map-swap
  (storage/with-tx t
    (let [x (storage/create t (->Test 0))
          y (storage/get-one t (aggregate/id @x))
          _ (dosync (identity/alter x update :counter inc))]
      (t/is (= 1 (:counter @x) (:counter @y))))))

(t/deftest concurrency
  (let [test (storage/tx-create (->Test 0))
        id   (aggregate/id test)
        n    10
        _    (->> (repeatedly #(future (storage/tx-alter id update :counter inc)))
                  (take n)
                  (doall)
                  (map deref)
                  (doall))
        test (storage/tx-get-one id)]
    (t/is (= n (:counter test)))))

(t/deftest inner-concurrency
  (let [test (storage/tx-create (->Test 0))
        id   (aggregate/id test)
        n    10
        _    (storage/with-tx t
               (->> (repeatedly #(future (as-> id <>
                                           (storage/get-one t <>)
                                           (dosync (identity/alter <> update :counter inc)))))
                    (take n)
                    (doall)
                    (map deref)
                    (doall)))
        test (storage/tx-get-one id)]
    (t/is (= n (:counter test)))))
