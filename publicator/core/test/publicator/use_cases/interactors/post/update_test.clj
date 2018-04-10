(ns publicator.use-cases.interactors.post.update-test
  (:require
   [publicator.use-cases.interactors.post.update :as sut]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.domain.services.user-posts :as user-posts]
   [publicator.use-cases.test.fixtures :as fixtures]
   [publicator.utils.fixtures :as utils.fixtures]
   [publicator.use-cases.test.factories :as factories]
   [clojure.spec.alpha :as s]
   [clojure.test :as t]))

(t/use-fixtures :each fixtures/fakes)
(t/use-fixtures :once utils.fixtures/instrument)

(t/deftest process
  (let [user       (factories/create-user)
        _          (user-session/log-in! user)
        post       (factories/create-post)
        _          (storage/tx-alter (:id user) user-posts/add-post post)
        params     (factories/gen ::sut/params)
        [tag post] (sut/process (:id post)  params)]
    (t/testing "success"
      (t/is (= ::sut/processed tag)))
    (t/testing "updated"
      (t/is (= params (select-keys post (keys params)))))))

(t/deftest logged-out
  (let [post   (factories/create-post)
        params (factories/gen ::sut/params)
        [tag]  (sut/process (:id post) params)]
    (t/testing "has error"
      (t/is (=  ::sut/logged-out tag)))))

(t/deftest another-author
  (let [user   (factories/create-user)
        _      (user-session/log-in! user)
        post   (factories/create-post)
        params (factories/gen ::sut/params)
        [tag]  (sut/process (:id post) params)]
    (t/testing "error"
      (t/is (= ::sut/not-authorized tag)))))

(t/deftest invalid-params
  (let [user    (factories/create-user)
        _       (user-session/log-in! user)
        post    (factories/create-post)
        params  {}
        [tag _] (sut/process (:id post) params)]
    (t/testing "error"
      (t/is (= ::sut/invalid-params tag)))))

(t/deftest not-found
  (let [user     (factories/create-user)
        _        (user-session/log-in! user)
        params   (factories/gen ::sut/params)
        wrong-id -1
        [tag]    (sut/process wrong-id params)]
    (t/testing "error"
      (t/is (= ::sut/not-found tag)))))

(t/deftest initial-params
  (let [user    (factories/create-user)
        _       (user-session/log-in! user)
        post    (factories/create-post)
        _       (storage/tx-alter (:id user) user-posts/add-post post)
        [tag _] (sut/initial-params (:id post))]
    (t/testing "success"
      (t/is (=  ::sut/initial-params tag)))))
