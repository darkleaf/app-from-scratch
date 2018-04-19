(ns publicator.web.presenters.user.log-in
  (:require
   [publicator.web.transit :as t]
   [sibiro.core :as sibiro]))

(defn form [{:keys [routes]}]
  {:widget :submit, :url (sibiro/path-for routes :user.log-in/handler), :method :post, :nested
   {:widget :group, :nested
    [:login {:widget :input, :label "Login"}
     :password {:widget :input, :label "Password", :type "password"}]}})

(defn initial-params [{:keys [routes], :as ctx} params]
  {:form-description (t/write (form ctx))
   :form-data        (t/write params)
   :form-errors      (t/write {})})

(defn invalid-params [ctx params explain-data])

(defn authentication-failed [ctx params])
