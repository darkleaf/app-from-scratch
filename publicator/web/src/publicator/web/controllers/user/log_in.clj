(ns publicator.web.controllers.user.log-in
  (:require
   [publicator.use-cases.interactors.user.log-in :as interactor]
   [publicator.web.controllers.base :as base]))

(defn form [req]
  (let [resp (interactor/inital-params)]
    (base/handle resp)))

(defn handler [{:keys [params]}]
  (let [resp (interactor/process params)]
    (base/handle resp)))

(defmethod base/handle ::interactor/initial-params [[_ params]]
  {:status 200})

(defmethod base/handle ::interactor/processed [_]
  {:status 302})

(defn routes []
  #{[:get "/log-in" form :user.log-in/form]
    [:post "/log-in" #'handler :user.log-in/handler]})
