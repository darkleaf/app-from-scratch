(ns publicator.web.controllers.base
  (:require
   [publicator.web.template :as template]
   [publicator.web.form-renderer :as form-renderer]
   [publicator.web.transit :as transit]
   [ring.util.http-response :as http-response]))

(defmulti handle (fn [ctx resp] (first resp)))

(defmethod handle ::forbidden [_ _]
  {:status 403
   :headers {}
   :body "forbidden"})

(defmethod handle ::not-found [_ _]
  {:status 404
   :headers {}
   :body "not-found"})

(defn render
  ([template] (render template {}))
  ([template model]
   (-> (template/render template model)
       (http-response/ok)
       (http-response/content-type "text/html"))))

(defn form [form]
  (-> form
      form-renderer/render
      http-response/ok
      (http-response/content-type "text/html")))

(defn redirect [url]
  (http-response/found url))

(defn redirect-form [url]
  (http-response/created url))

(defn errors [errors]
  (-> errors
      transit/write
      http-response/unprocessable-entity
      (http-response/content-type "application/transit+json")))
