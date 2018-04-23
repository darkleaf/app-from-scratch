(ns publicator.web.controllers.user.log-out
  (:require
   [publicator.use-cases.interactors.user.log-out :as interactor]
   [publicator.web.controllers.base :as base]
   [publicator.web.url-helpers :as url-helpers]))

(defn handler [_]
  (let [result (interactor/process)]
    (base/handle nil result)))

(defmethod base/handle ::interactor/processed [_ _]
  (base/redirect (url-helpers/path-for :pages/root)))

(derive ::interactor/already-logged-out ::base/forbidden)

(def routes
  #{[:post "/log-out" #'handler :user.log-out/handler]})
