(ns publicator.web.handler
  (:require
   [sibiro.extras]
   [ring.middleware.params :as ring.params]
   [ring.middleware.keyword-params :as ring.keyword-params]
   [ring.util.request :as ring.request]
   [publicator.web.routing :as routing]
   [publicator.web.middlewares.layout :as layout]
   [publicator.web.middlewares.session :as session]
   [publicator.web.transit :as t]))

(defn- wrap-transit-params [handler]
  (fn [req]
    (let [req (if (= "application/transit+json"
                     (ring.request/content-type req))
                (assoc req :transit-params (-> req
                                               ring.request/body-string
                                               t/read))
                req)]
      (handler req))))

(defn build
  ([] (build {}))
  ([config]
   (-> routing/routes
       sibiro.extras/make-handler
       layout/wrap
       (session/wrap (:session config {}))
       wrap-transit-params
       ring.keyword-params/wrap-keyword-params
       ring.params/wrap-params)))
