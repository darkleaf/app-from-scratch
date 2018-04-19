(ns publicator.web.controllers.post.list
  (:require
   [publicator.use-cases.interactors.post.list :as interactor]
   [publicator.web.presenters.post.list :as presenter]
   [publicator.web.controllers.base :as base]
   [publicator.web.template :as template]
   [ring.util.http-response :as http-response]))

(defn handler [req]
  (let [result (interactor/process)
        ctx    req]
    (base/handle result ctx)))

(defmethod base/handle ::interactor/processed [[_ posts] ctx]
  (let [model (presenter/processed ctx posts)]
    (-> (template/render "post/list" model)
        (http-response/ok)
        (http-response/content-type "text/html"))))

(defn routes []
  #{[:get "/posts" handler :post.list/handler]})
