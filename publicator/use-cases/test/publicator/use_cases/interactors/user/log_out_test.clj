(ns publicator.use-cases.interactors.user.log-out-test
  (:require
   [publicator.use-cases.interactors.user.log-out :as sut]
   [publicator.domain.aggregates.user :as user]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.use-cases.test.fixtures :as fixtures]
   [publicator.use-cases.test-fixtures :as test-fixtures]
   [publicator.use-cases.test.factories :as factories]
   [clojure.test :as t]))

(t/use-fixtures :each fixtures/fakes)
(t/use-fixtures :once test-fixtures/instrument)

(t/deftest main
  (let [user (factories/create-user)
        _    (user-session/log-in! user)
        resp (sut/process)]
    (t/testing "success"
      (t/is (= (:type resp) ::sut/processed)))
    (t/testing "logged out"
      (t/is (user-session/logged-out?)))))

(t/deftest already-logged-out
  (let [resp (sut/process)]
    (t/testing "has error"
      (t/is (= (:type resp) ::sut/already-logged-out)))))
