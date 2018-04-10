(ns publicator.domain.aggregates.post-test
  (:require
   [publicator.domain.aggregates.post :as sut]
   [publicator.domain.test.fixtures :as fixtures]
   [publicator.utils.fixtures :as utils.fixtures]
   [publicator.domain.test.factories :as factories]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [clojure.spec.alpha :as s]
   [clojure.test :as t]))

(t/use-fixtures :each fixtures/fakes)
(t/use-fixtures :once utils.fixtures/instrument)

(t/deftest build
  (let [params {:title "John Doe"
                :content "Lorem ipsum"}
        post   (sut/build params)]
    (t/is (sut/post? post))))

(t/deftest aggregate
  (t/testing "id"
    (let [post (factories/build-post)]
      (t/is (= (:id post)
               (aggregate/id post)))))
  (t/testing "spec"
    (let [post (factories/build-post)
          spec (aggregate/spec post)]
      (t/is (s/valid? spec post)))))
