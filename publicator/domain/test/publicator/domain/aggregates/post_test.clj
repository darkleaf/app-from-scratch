(ns publicator.domain.aggregates.post-test
  (:require
   [publicator.domain.aggregates.post :as sut]
   [publicator.domain.test.fixtures :as fixtures]
   [publicator.domain.test-fixtures :as test-fixtures]
   [publicator.domain.test.factories :as factories]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [clojure.test :as t]))

(t/use-fixtures :each fixtures/fakes)
(t/use-fixtures :once test-fixtures/instrument)

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
  (t/testing "valid?"
    (let [post (factories/build-post)]
      (t/is (aggregate/valid? post))
      (t/is (not (aggregate/valid? (assoc post :id nil)))))))
