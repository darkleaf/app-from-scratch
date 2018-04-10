(ns publicator.use-cases.interactors.user.log-in-test
  (:require
   [publicator.use-cases.interactors.user.log-in :as sut]
   [publicator.domain.aggregates.user :as user]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.use-cases.test.fixtures :as fixtures]
   [publicator.utils.fixtures :as utils.fixtures]
   [publicator.use-cases.test.factories :as factories]
   [clojure.test :as t]))

(t/use-fixtures :each fixtures/fakes)
(t/use-fixtures :once utils.fixtures/instrument)

(t/deftest main
  (let [password (factories/gen ::user/password)
        user     (factories/create-user {:password password})
        params   {:login (:login user), :password password}
        [tag]  (sut/process params)]
    (t/testing "success"
      (t/is (= ::sut/processed tag)))
    (t/testing "logged in"
      (t/is (user-session/logged-in?)))))

(t/deftest wrong-login
  (let [params {:login    "john_doe"
                :password "secret password"}
        [tag]  (sut/process params)]
    (t/testing "has error"
      (t/is (=  ::sut/authentication-failed tag)))))

(t/deftest wrong-password
  (let [user   (factories/create-user)
        params {:login    (:login user)
                :password "wrong password"}
        [tag]  (sut/process params)]
    (t/testing "has error"
      (t/is (= ::sut/authentication-failed tag)))))

(t/deftest already-logged-in
  (let [user   (factories/create-user)
        _      (user-session/log-in! user)
        params {:login    "foo"
                :password "bar"}
        [tag]  (sut/process params)]
    (t/testing "has error"
      (t/is (= ::sut/already-logged-in tag)))))

(t/deftest invalid-params
  (let [params {}
        [tag]  (sut/process params)]
    (t/testing "error"
      (t/is (= ::sut/invalid-params tag)))))
