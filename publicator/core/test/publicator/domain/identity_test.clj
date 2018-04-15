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
  (spec [_] (fn [_] (some? property)))
  (wrap-update [this] (assoc this ::updated true)))

(defrecord OtherAggregate [id property]
  aggregate/Aggregate
  (id [_] id)
  (spec [_] (fn [_] (some? property)))
  (wrap-update [this] this))

(t/deftest identity-test
  (let [iagg (sut/build (->Aggregate 1 true))]
    (t/testing "spec"
      (t/is (s/valid? ::sut/identity iagg))
      (t/is (not (s/valid? ::sut/identity (ref nil)))))
    (t/testing "validator"
      (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Aggregate id was changed."
                              (dosync (sut/alter iagg assoc :id 2))))
      (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Aggregate class was changed."
                              (dosync (sut/alter iagg map->OtherAggregate))))
      (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Aggregate was invalid. "
                              (dosync (sut/alter iagg assoc :property nil)))))))

(t/deftest alter-test
  (let [iagg (sut/build (->Aggregate 1 true))]
    (t/is (-> @iagg ::updated nil?))
    (dosync (sut/alter iagg assoc :property false))
    (t/is (-> @iagg ::updated true?))))
