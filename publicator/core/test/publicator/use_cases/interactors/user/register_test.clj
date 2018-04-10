(ns publicator.use-cases.interactors.user.register-test
  (:require
   [publicator.use-cases.interactors.user.register :as sut]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.use-cases.abstractions.user-queries :as user-q]
   [publicator.use-cases.test.fixtures :as fixtures]
   [publicator.utils.fixtures :as utils.fixtures]
   [publicator.use-cases.test.factories :as factories]
   [clojure.test :as t]))

(t/use-fixtures :each fixtures/fakes)
(t/use-fixtures :once utils.fixtures/instrument)

(t/deftest process
  (let [params     (factories/gen ::sut/params)
        [tag user] (sut/process params)]
    (t/testing "success"
      (t/is (= ::sut/processed tag)))
    (t/testing "logged in"
      (t/is (user-session/logged-in?)))
    (t/testing "persisted"
      (t/is (some? (storage/tx-get-one (:id user)))))))

(t/deftest already-registered
  (let [params (factories/gen ::sut/params)
        _      (factories/create-user {:login (:login params)})
        [tag]  (sut/process params)]
    (t/testing "has error"
      (t/is (= ::sut/already-registered tag)))
    (t/testing "not sign in"
      (t/is (user-session/logged-out?)))))

(t/deftest already-logged-in
  (let [user   (factories/create-user)
        _      (user-session/log-in! user)
        params (factories/gen ::sut/params)
        [tag]  (sut/process params)]
    (t/testing "has error"
      (t/is (= ::sut/already-logged-in tag)))))

(t/deftest invalid-params
  (let [params  {}
        [tag _] (sut/process params)]
    (t/testing "error"
      (t/is (= ::sut/invalid-params tag)))))
