(ns publicator.web.handler
  (:require
   [sibiro.extras]
   [ring.middleware.params :as ring.params]
   [ring.middleware.keyword-params :as ring.keyword-params]
   [ring.middleware.resource :as ring.resource]
   [publicator.web.routing :as routing]
   [publicator.web.middlewares.layout :as layout]
   [publicator.web.middlewares.session :as session]))

(defn- wrap-routes [handler routes]
  (fn [req]
    (-> req
        (assoc :routes routes)
        handler)))

(defn build
  ([] (build {}))
  ([config]
   (let [routes  (routing/build)
         handler (sibiro.extras/make-handler routes)]
     (-> handler
         layout/wrap
         (wrap-routes routes)
         (session/wrap (:session config {}))
         ring.keyword-params/wrap-keyword-params
         ring.params/wrap-params
         (ring.resource/wrap-resource "publicator/web/public")))))
