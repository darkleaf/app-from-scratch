(ns publicator.web.presenters.user.log-in
  (:require
   [sibiro.core :as sibiro]))

(defn initial-params [{:keys [routes]} params]
  {:params params
   :errors {}
   :form   {:action (sibiro/path-for routes :user.log-in/handler)
            :method :post}})

(defn invalid-params [ctx params explain-data])

(defn authentication-failed [ctx params])
