(ns publicator.web.controllers.user.log-out
  (:require
   [publicator.use-cases.interactors.user.log-out :as interactor]
   [publicator.web.controllers.base :as base]))

(defn handler [_]
  (let [result (interactor/process)]
    (base/handle result)))

(defmethod base/handle ::interactor/processed [_]
  (base/redirect :pages/root))

(derive ::interactor/already-logged-out ::base/forbidden)

(def routes
  #{[:post "/log-out" #'handler :user.log-out/handler]})
