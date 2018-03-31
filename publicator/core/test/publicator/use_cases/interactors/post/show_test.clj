(ns publicator.use-cases.interactors.post.show-test
  (:require
   [publicator.use-cases.interactors.post.show :as sut]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.domain.services.user-posts :as user-posts]
   [publicator.use-cases.test.fixtures :as fixtures]
   [publicator.use-cases.test-fixtures :as test-fixtures]
   [publicator.use-cases.test.factories :as factories]
   [clojure.test :as t]))

(t/use-fixtures :each fixtures/fakes)
(t/use-fixtures :once test-fixtures/instrument)

(t/deftest process
  (let [user (factories/create-user)
        post (factories/create-post)
        _    (storage/tx-alter (:id user) user-posts/add-post post)]
    (t/testing "guest"
      (let [resp (sut/process (:id post))
            post (:post resp)]
        (t/is (= ::sut/processed (:type resp)))
        (t/is (some? post))))))
