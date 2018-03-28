(ns publicator.use-cases.interactors.user.register-test
  (:require
   [publicator.use-cases.interactors.user.register :as sut]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.use-cases.abstractions.user-queries :as user-q]
   [publicator.use-cases.test.fixtures :as fixtures]
   [publicator.use-cases.test-fixtures :as test-fixtures]
   [publicator.use-cases.test.factories :as factories]
   [clojure.test :as t]))

(t/use-fixtures :each fixtures/fakes)
(t/use-fixtures :once test-fixtures/instrument)

(t/deftest process
  (let [params (factories/gen ::sut/params)
        resp   (sut/process params)
        user   (:user resp)]
    (t/testing "success"
      (t/is (= (:type resp) ::sut/processed)))
    (t/testing "logged in"
      (t/is (user-session/logged-in?)))
    (t/testing "persisted"
      (t/is (some? (storage/tx-get-one (:id user)))))))

(t/deftest already-registered
  (let [params (factories/gen ::sut/params)
        _      (factories/create-user {:login (:login params)})
        resp   (sut/process params)]
    (t/testing "has error"
      (t/is (= (:type resp)
               ::sut/already-registered)))
    (t/testing "not sign in"
      (t/is (user-session/logged-out?)))))

(t/deftest already-logged-in
  (let [user    (factories/create-user)
        _       (user-session/log-in! user)
        params  (factories/gen ::sut/params)
        resp    (sut/process params)]
    (t/testing "has error"
      (t/is (=  (:type resp)
                ::sut/already-logged-in)))))

(t/deftest invalid-params
  (let [params {}
        resp   (sut/process params)]
    (t/testing "error"
      (t/is (= (:type resp) ::sut/invalid-params))
      (t/is (contains? resp  :explain-data)))))
