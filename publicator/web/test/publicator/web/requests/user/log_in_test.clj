(ns publicator.web.requests.user.log-in-test
  (:require
   [clojure.test :as t]
   [publicator.utils.fixtures :as utils.fixtures]
   [ring.util.http-predicates :as http-predicates]
   [ring.mock.request :as mock.request]
   [publicator.web.handler :as handler]
   [publicator.use-cases.interactors.user.log-in :as interactor]
   [publicator.use-cases.test.factories :as factories]))

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
