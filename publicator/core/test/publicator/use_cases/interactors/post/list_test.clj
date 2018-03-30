(ns publicator.use-cases.interactors.post.list-test
  (:require
   [publicator.use-cases.interactors.post.list :as sut]
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
  (let [user         (factories/create-user)
        post         (factories/create-post)
        another-post (factories/create-post)
        _            (storage/tx-alter (:id user) user-posts/add-post post)]
    (t/testing "guest"
      (let [resp  (sut/process)
            posts (:posts resp)]
        (t/testing "success"
          (t/is (= (:type resp) ::sut/processed))
          (t/is (not-empty posts)))
        (t/testing "can not edit"
          (t/is (every? #(-> % ::sut/can-edit? false?) posts)))))
    (t/testing "logged in"
      (let [_     (user-session/log-in! user)
            resp  (sut/process)
            posts (:posts resp)]
        (t/testing "success"
          (t/is (= (:type resp) ::sut/processed))
          (t/is (not-empty posts)))
        (t/testing "can edit"
          (t/is (some #(-> % ::sut/can-edit? true?) posts)))))))
