(ns publicator.web.requests.pages.root-test
  (:require
   [clojure.test :as t]
   [publicator.utils.fixtures :as utils.fixtures]
   [ring.util.http-predicates :as http-predicates]
   [ring.mock.request :as mock.request]
   [publicator.web.handler :as handler]))

(t/use-fixtures :once utils.fixtures/instrument)

(t/deftest root
  (let [handler (handler/build)
        req     (mock.request/request :get "/")
        resp    (handler req)]
    (t/is (http-predicates/ok? resp))))
