(ns publicator.use-cases.interactors.post.update-test
  (:require
   [publicator.use-cases.interactors.post.update :as sut]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.domain.services.user-posts :as user-posts]
   [publicator.use-cases.test.fixtures :as fixtures]
   [publicator.use-cases.test-fixtures :as test-fixtures]
   [publicator.use-cases.test.factories :as factories]
   [clojure.spec.alpha :as s]
   [clojure.test :as t]))

(t/use-fixtures :each fixtures/fakes)
(t/use-fixtures :once test-fixtures/instrument)

(t/deftest process
  (let [user   (factories/create-user)
        _      (user-session/log-in! user)
        post   (factories/create-post)
        _      (storage/tx-alter (:id user) user-posts/add-post post)
        params (factories/gen ::sut/params)
        resp   (sut/process (:id post)  params)
        post   (:post resp)]
    (t/testing "success"
      (t/is (= (:type resp) ::sut/processed))
      (t/is (some? post)))
    (t/testing "updated"
      (t/is (= params (select-keys post (keys params)))))))

(t/deftest logged-out
  (let [post   (factories/create-post)
        params (factories/gen ::sut/params)
        resp   (sut/process (:id post) params)]
    (t/testing "has error"
      (t/is (=  (:type resp)
                ::sut/logged-out)))))

(t/deftest another-author
  (let [user   (factories/create-user)
        _      (user-session/log-in! user)
        post   (factories/create-post)
        params (factories/gen ::sut/params)
        resp   (sut/process (:id post) params)]
    (t/testing "error"
      (t/is (= (:type resp) ::sut/not-authorized)))))

(t/deftest invalid-params
  (let [user   (factories/create-user)
        _      (user-session/log-in! user)
        post   (factories/create-post)
        params {}
        resp   (sut/process (:id post) params)]
    (t/testing "error"
      (t/is (= (:type resp) ::sut/invalid-params))
      (t/is (contains? resp  :explain-data)))))

(t/deftest not-found
  (let [user     (factories/create-user)
        _        (user-session/log-in! user)
        params   (factories/gen ::sut/params)
        wrong-id -1
        resp     (sut/process wrong-id params)]
    (t/testing "error"
      (t/is (= (:type resp) ::sut/not-found)))))

(t/deftest initial-params
  (let [user (factories/create-user)
        _    (user-session/log-in! user)
        post (factories/create-post)
        _    (storage/tx-alter (:id user) user-posts/add-post post)
        resp (sut/initial-params (:id post))]
    (t/testing "success"
      (t/is (= (:type resp) ::sut/initial-params))
      (t/is (s/valid? ::sut/params (:initial-params resp))))))
