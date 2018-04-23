(ns publicator.web.controllers.post.show
  (:require
   [publicator.use-cases.interactors.post.show :as interactor]
   [publicator.web.presenters.post.show :as presenter]
   [publicator.web.controllers.base :as base]))

(defn handler [{:keys [route-params]}]
  (let [id     (-> route-params :id Integer.)
        result (interactor/process id)]
    (base/handle nil result)))

(defmethod base/handle ::interactor/processed [_ [_ posts]]
  (let [model (presenter/processed posts)]
    (base/render "post/show" model)))

(derive ::interactor/not-found ::base/not-found)

(def routes
  #{[:get "/posts/:id{\\d+}" #'handler :post.show/handler]})
