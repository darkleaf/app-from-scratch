(ns publicator.web.forms.post.params
  (:require
   [publicator.web.url-helpers :as url-helpers]))

(defn description [{:keys [url method]}]
  {:widget :submit, :name "Готово"
   :url url, :method method :nested
   {:widget :group, :nested
    [:title {:widget :input, :label "Заголовок"}
     :content {:widget :textarea, :label "Содержание"}]}})

(defn build [cfg initial-params]
  {:initial-data initial-params
   :errors       {}
   :description  (description cfg)})
