(ns publicator.web.controllers.post.list
  (:require
   [publicator.use-cases.interactors.post.list :as interactor]
   [publicator.web.presenters.post.list :as presenter]
   [publicator.web.controllers.base :as base]))

(defn handler [req]
  (let [result (interactor/process)]
    (base/handle nil result)))

(defmethod base/handle ::interactor/processed [_ [_ posts]]
  (let [model (presenter/processed posts)]
    (base/render "post/list" model)))

(def routes
  #{[:get "/posts" #'handler :post.list/handler]})
