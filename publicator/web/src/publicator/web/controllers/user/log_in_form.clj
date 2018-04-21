(ns publicator.web.controllers.user.log-in-form
  (:require
   [publicator.web.url-helpers :as url-helpers]))

(defn description []
  {:widget :submit, :url (url-helpers/path-for :user.log-in/handler), :method :post, :nested
   {:widget :group, :nested
    [:login {:widget :input, :label "Логин"}
     :password {:widget :input, :label "Пароль", :type "password"}]}})

(defn build [initial-params errors]
  {:initial-data initial-params
   :errors       errors
   :description  (description)})

(defn authentication-failed-error []
  {:form-ujs/error "Неверный логин или пароль"})
