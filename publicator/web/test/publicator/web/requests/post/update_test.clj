(ns publicator.web.requests.post.update-test
  (:require
   [clojure.test :as t]
   [publicator.utils.fixtures :as utils.fixtures]
   [ring.util.http-predicates :as http-predicates]
   [ring.mock.request :as mock.request]
   [publicator.web.handler :as handler]
   [publicator.use-cases.interactors.post.update :as interactor]
   [publicator.use-cases.test.factories :as factories]
   [clojure.spec.alpha :as s]
   [clojure.template :as template]))

(t/use-fixtures :once utils.fixtures/instrument)

(t/deftest form
  (let [handler        (handler/build)
        req            (mock.request/request :get "/posts/1/edit")
        called?        (atom false)
        initial-params (fn [id]
                         (reset! called? true)
                         (t/is (= 1 id))
                         (factories/gen ::interactor/initial-params))
        resp           (binding [interactor/*initial-params* initial-params]
                         (handler req))]
    (t/is @called?)
    (t/is (http-predicates/ok? resp))))

(t/deftest handler
  (let [handler (handler/build)
        params  (factories/gen ::interactor/params)
        req     (-> (mock.request/request :post "/posts/1/edit")
                    (assoc :transit-params params))
        called? (atom false)
        process (fn [id p]
                  (reset! called? true)
                  (t/is (= 1 id))
                  (t/is (= params p))
                  (factories/gen ::interactor/processed))
        resp    (binding [interactor/*process* process]
                  (handler req))]
    (t/is @called?)
    (t/is (http-predicates/created? resp))))

(template/do-template
 [test-name interactor-resp predicate]

 (t/deftest test-name
   (let [handler        (handler/build)
         req            (mock.request/request :get "/posts/1/edit")
         initial-params (fn [_] interactor-resp)
         resp           (binding [interactor/*initial-params* initial-params]
                          (handler req))]
     (t/is (predicate resp))))

 form-logged-out
 (factories/gen ::interactor/logged-out)
 http-predicates/forbidden?

 form-not-authorized
 (factories/gen ::interactor/not-authorized)
 http-predicates/forbidden?

 form-not-found
 (factories/gen ::interactor/not-found)
 http-predicates/not-found?)

(template/do-template
 [test-name interactor-resp predicate]

 (t/deftest test-name
   (let [handler (handler/build)
         req     (mock.request/request :post "/posts/1/edit")
         process (fn [_ _] interactor-resp)
         resp    (binding [interactor/*process* process]
                   (handler req))]
     (t/is (predicate resp))))

 handler-logged-out
 (factories/gen ::interactor/logged-out)
 http-predicates/forbidden?

 handler-not-authorized
 (factories/gen ::interactor/not-authorized)
 http-predicates/forbidden?

 handler-not-found
 (factories/gen ::interactor/not-found)
 http-predicates/not-found?

 handler-invalid-params
 [::interactor/invalid-params (s/explain-data ::interactor/params {})]
 http-predicates/unprocessable-entity?)
