(ns publicator.web.controllers.post.update
  (:require
   [publicator.use-cases.interactors.post.update :as interactor]
   [publicator.web.presenters.explain-data :as explain-data]
   [publicator.web.forms.post.params :as form]
   [publicator.web.controllers.base :as base]
   [publicator.web.url-helpers :as url-helpers]))

(defn form [{:keys [route-params]}]
  (let [id     (-> route-params :id Integer.)
        result (interactor/initial-params id)
        ctx    {:id id}]
    (base/handle ctx result)))

(defn handler [{:keys [transit-params route-params]}]
  (let [id     (-> route-params :id Integer.)
        result (interactor/process id transit-params)]
    (base/handle nil result)))

(defmethod base/handle ::interactor/initial-params [ctx [_ params]]
  (let [cfg  {:url    (url-helpers/path-for :post.update/handler {:id (-> ctx :id str)})
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
(derive ::interactor/not-authorized ::base/forbidden)
(derive ::interactor/not-found ::base/not-found)

(def routes
  #{[:get "/posts/:id{\\d+}/edit" #'form :post.update/form]
    [:post "/posts/:id{\\d+}/edit" #'handler :post.update/handler]})
