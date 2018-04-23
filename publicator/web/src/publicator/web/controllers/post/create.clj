(ns publicator.web.controllers.post.create
  (:require
   [publicator.use-cases.interactors.post.create :as interactor]
   [publicator.web.presenters.explain-data :as explain-data]
   [publicator.web.forms.post.params :as form]
   [publicator.web.controllers.base :as base]
   [publicator.web.url-helpers :as url-helpers]))

(defn form [req]
  (let [result (interactor/initial-params)]
    (base/handle nil result)))

(defn handler [{:keys [transit-params]}]
  (let [result (interactor/process transit-params)]
    (base/handle nil result)))

(defmethod base/handle ::interactor/initial-params [_ [_ params]]
  (let [cfg  {:url    (url-helpers/path-for :post.create/handler)
              :method :post}
        form (form/build cfg params)]
    (base/form form)))

(defmethod base/handle ::interactor/processed [_ _]
  (base/redirect-form (url-helpers/path-for :pages/root)))

(defmethod base/handle ::interactor/invalid-params [_ [_ explain-data]]
  (-> explain-data
      explain-data/->errors
      base/errors))

(derive ::interactor/logged-out ::base/forbidden)

(def routes
  #{[:get "/new-post" #'form :post.create/form]
    [:post "/new-post" #'handler :post.create/handler]})
