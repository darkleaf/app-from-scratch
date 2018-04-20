(ns publicator.web.presenters.post.list
  (:require
   [publicator.use-cases.interactors.post.list :as interactor]
   [publicator.domain.aggregates.user :as user]
   [publicator.web.url-helpers :as url-helpers]))

(defn- post->model [post]
  {:id             (:id post)
   :url            (url-helpers/path-for :post.list/handler)
   :edit-url       (url-helpers/path-for :post.list/handler)
   :title          (:title post)
   :can-edit?      (::interactor/can-edit? post)
   :user-full-name (::user/full-name post)})

(defn processed [posts]
  {:posts (map post->model posts)})
