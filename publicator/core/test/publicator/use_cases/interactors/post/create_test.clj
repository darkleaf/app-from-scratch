(ns publicator.use-cases.interactors.post.create-test
  (:require
   [publicator.use-cases.interactors.post.create :as sut]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.domain.services.user-posts :as user-posts]
   [publicator.use-cases.test.fixtures :as fixtures]
   [publicator.utils.fixtures :as utils.fixtures]
   [publicator.use-cases.test.factories :as factories]
   [clojure.test :as t]))

(t/use-fixtures :each fixtures/fakes)
(t/use-fixtures :once utils.fixtures/instrument)

(t/deftest process
  (let [user       (factories/create-user)
        _          (user-session/log-in! user)
        params     (factories/gen ::sut/params)
        [tag post] (sut/process params)
        user       (storage/tx-get-one (:id user))]
    (t/testing "success"
      (t/is (= ::sut/processed tag)))
    (t/testing "update user"
      (t/is (user-posts/author? user post)))))

(t/deftest logged-out
  (let [params (factories/gen ::sut/params)
        [tag]  (sut/process params)]
    (t/testing "has error"
      (t/is (=  ::sut/logged-out tag)))))

(t/deftest invalid-params
  (let [user    (factories/create-user)
        _       (user-session/log-in! user)
        params  {}
        [tag _] (sut/process params)]
    (t/testing "error"
      (t/is (=  ::sut/invalid-params tag)))))

(t/deftest initial-params
  (let [user    (factories/create-user)
        _       (user-session/log-in! user)
        [tag _] (sut/initial-params)]
    (t/is (= ::sut/initial-params tag))))

(t/deftest initial-params
  (let [[tag] (sut/initial-params)]
    (t/is (= ::sut/logged-out tag))))
