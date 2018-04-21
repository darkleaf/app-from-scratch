(ns publicator.web.requests.user.log-in-test
  (:require
   [clojure.test :as t]
   [publicator.utils.fixtures :as utils.fixtures]
   [ring.util.http-predicates :as http-predicates]
   [ring.mock.request :as mock.request]
   [publicator.web.handler :as handler]
   [publicator.use-cases.interactors.user.log-in :as interactor]
   [publicator.use-cases.test.factories :as factories]
   [clojure.spec.alpha :as s]))

(t/use-fixtures :once utils.fixtures/instrument)

(t/deftest form
  (let [handler        (handler/build)
        req            (mock.request/request :get "/log-in")
        called?        (atom false)
        initial-params (fn []
                         (reset! called? true)
                         (factories/gen ::interactor/initial-params))
        resp           (binding [interactor/*initial-params* initial-params]
                         (handler req))]
    (t/is @called?)
    (t/is (http-predicates/ok? resp))))

(t/deftest handler
  (let [handler (handler/build)
        params  (factories/gen ::interactor/params)
        req     (-> (mock.request/request :post "/log-in")
                    (assoc :transit-params params))
        called? (atom false)
        process (fn [p]
                  (reset! called? true)
                  (t/is (= params p))
                  (factories/gen ::interactor/processed))
        resp    (binding [interactor/*process* process]
                  (handler req))]
    (t/is @called?)
    (t/is (http-predicates/created? resp))))

(t/deftest form-already-logged-in
  (let [handler        (handler/build)
        req            (mock.request/request :get "/log-in")
        called?        (atom false)
        initial-params (fn []
                         (reset! called? true)
                         (factories/gen ::interactor/already-logged-in))
        resp           (binding [interactor/*initial-params* initial-params]
                         (handler req))]
    (t/is @called?)
    (t/is (http-predicates/forbidden? resp))))

(t/deftest handler-already-logged-in
  (let [handler (handler/build)
        req     (mock.request/request :post "/log-in")
        called? (atom false)
        process (fn [_]
                  (reset! called? true)
                  (factories/gen ::interactor/already-logged-in))
        resp    (binding [interactor/*process* process]
                  (handler req))]
    (t/is @called?)
    (t/is (http-predicates/forbidden? resp))))

(t/deftest handler-invalid-params
  (let [handler (handler/build)
        req     (mock.request/request :post "/log-in")
        called? (atom false)
        process (fn [_]
                  (reset! called? true)
                  [::interactor/invalid-params (s/explain-data ::interactor/params {})])
        resp    (binding [interactor/*process* process]
                  (handler req))]
    (t/is @called?)
    (t/is (http-predicates/unprocessable-entity? resp))))

(t/deftest handler-authentication-failed
  (let [handler (handler/build)
        req     (mock.request/request :post "/log-in")
        called? (atom false)
        process (fn [_]
                  (reset! called? true)
                  (factories/gen ::interactor/authentication-failed))
        resp    (binding [interactor/*process* process]
                  (handler req))]
    (t/is @called?)
    (t/is (http-predicates/unprocessable-entity? resp))))
