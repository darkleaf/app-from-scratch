(ns publicator.web.controllers.user.log-in
  (:require
   [publicator.use-cases.interactors.user.log-in :as interactor]
   [publicator.web.presenters.explain-data :as explain-data]
   [publicator.web.controllers.user.form :as form]
   [publicator.web.controllers.base :as base]))

(defn form [req]
  (let [result (interactor/inital-params)]
    (base/handle result)))

(defn handler [{:keys [transit-params]}]
  (let [result (interactor/process transit-params)]
    (base/handle result)))

(defmethod base/handle ::interactor/initial-params [[_ params]]
  (let [form (form/build params {})]
    (base/form form)))

(defmethod base/handle ::interactor/processed [_]
  (base/redirect-form :pages/root))

(defmethod base/handle ::interactor/invalid-params [[_ explain-data]]
  (-> explain-data
      explain-data/->errors
      base/errors))

(defmethod base/handle ::interactor/authentication-failed [_]
  (-> (form/authentication-failed-error)
      base/errors))

(derive ::interactor/already-logged-in ::base/forbidden)

(def routes
  #{[:get "/log-in" #'form :user.log-in/form]
    [:post "/log-in" #'handler :user.log-in/handler]})
