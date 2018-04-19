(ns publicator.web.controllers.user.log-in
  (:require
   [publicator.use-cases.interactors.user.log-in :as interactor]
   [publicator.web.presenters.user.log-in :as presenter]
   [publicator.web.controllers.base :as base]
   [publicator.web.presenters.explain-data :as explain-data]
   [publicator.web.template :as template]
   [publicator.web.transit :as t]
   [ring.util.http-response :as http-response]))

(defn form [req]
  (let [result (interactor/inital-params)
        ctx    req]
    (base/handle result ctx)))

(defn handler [{:keys [transit-params] :as req}]
  (let [result (interactor/process transit-params)
        ctx    req]
    (base/handle result ctx)))

(defmethod base/handle ::interactor/initial-params [[_ params] ctx]
  (let [model (presenter/initial-params ctx params)]
    (-> (template/render "user/log-in" model)
        (http-response/ok)
        (http-response/content-type "text/html"))))

(defmethod base/handle ::interactor/processed [_ _]
  {:status 302})


(defmethod base/handle ::interactor/invalid-params [[_ explain-data] _]
  {:status 422
   :body (t/write (explain-data/->errors explain-data))})


(defn routes []
  #{[:get "/log-in" form :user.log-in/form]
    [:post "/log-in" handler :user.log-in/handler]})
