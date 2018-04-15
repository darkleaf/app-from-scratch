(ns publicator.web.presenters.user.log-in
  (:require
   [sibiro.core :as sibiro]))

(defn initial-params [params {:keys [routes]}]
  {:params params
   :errors {}
   :form   {:action (sibiro/path-for routes :user.log-in/handler)
            :method :post}})

(defn invalid-params [params explain-data ctx])

(defn authentication-failed [params ctx])
