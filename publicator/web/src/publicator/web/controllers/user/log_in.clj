(ns publicator.web.controllers.user.log-in
  (:require
   [publicator.use-cases.interactors.user.log-in :as interactor]
   [publicator.web.presenters.explain-data :as explain-data]
   [publicator.web.forms.user.log-in :as form]
   [publicator.web.controllers.base :as base]
   [publicator.web.url-helpers :as url-helpers]))

(defn form [req]
  (let [result (interactor/inital-params)]
    (base/handle nil result)))

(defn handler [{:keys [transit-params]}]
  (let [result (interactor/process transit-params)]
    (base/handle nil result)))

(defmethod base/handle ::interactor/initial-params [_ [_ params]]
  (let [form (form/build params)]
    (base/form form)))

(defmethod base/handle ::interactor/processed [_ _]
  (base/redirect-form (url-helpers/path-for :pages/root)))

(defmethod base/handle ::interactor/invalid-params [_ [_ explain-data]]
  (-> explain-data
      explain-data/->errors
      base/errors))

(defmethod base/handle ::interactor/authentication-failed [_ _]
  (-> (form/authentication-failed-error)
      base/errors))

(derive ::interactor/already-logged-in ::base/forbidden)

(def routes
  #{[:get "/log-in" #'form :user.log-in/form]
    [:post "/log-in" #'handler :user.log-in/handler]})
