(ns publicator.use-cases.interactors.post.create-test
  (:require
   [publicator.use-cases.interactors.post.create :as sut]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.use-cases.test.fixtures :as fixtures]
   [publicator.use-cases.test-fixtures :as test-fixtures]
   [publicator.use-cases.test.factories :as factories]
   [clojure.test :as t]))

(t/use-fixtures :each fixtures/fakes)
(t/use-fixtures :once test-fixtures/instrument)

(t/deftest process
  (let [user    (factories/create-user)
        _       (user-session/log-in! user)
        params  (factories/gen ::sut/params)
        resp    (sut/process params)
        user    (storage/tx-get-one (:id user))
        post    (:post resp)
        post-id (:id post)]
    (t/testing "success"
      (t/is (= (:type resp) ::sut/processed))
      (t/is (some? post)))
    (t/testing "update user"
      (t/is (= [post-id]
               (:posts-ids user))))
    (t/testing "persisted"
      (t/is (some? (storage/tx-get-one post-id))))))

(t/deftest logged-out
  (let [params (factories/gen ::sut/params)
        resp   (sut/process params)]
    (t/testing "has error"
      (t/is (=  (:type resp)
                ::sut/logged-out)))))

(t/deftest invalid-params
  (let [user   (factories/create-user)
        _      (user-session/log-in! user)
        params {}
        resp   (sut/process params)]
    (t/testing "error"
      (t/is (= (:type resp) ::sut/invalid-params))
      (t/is (contains? resp  :explain-data)))))
