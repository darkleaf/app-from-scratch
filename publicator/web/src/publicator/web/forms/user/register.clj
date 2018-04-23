(ns publicator.web.forms.user.register
  (:require
   [publicator.web.url-helpers :as url-helpers]))

(defn description []
  {:widget :submit, :name "Зарегистрироваться"
   :url (url-helpers/path-for :user.register/handler), :method :post, :nested
   {:widget :group, :nested
    [:login {:widget :input, :label "Логин"}
     :full-name {:widget :input, :label "Полное имя"}
     :password {:widget :input, :label "Пароль", :type "password"}]}})

(defn build [initial-params]
  {:initial-data initial-params
   :errors       {}
   :description  (description)})
