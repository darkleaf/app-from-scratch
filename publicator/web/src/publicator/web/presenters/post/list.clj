(ns publicator.web.presenters.post.list
  (:require
   [publicator.use-cases.interactors.post.list :as interactor]
   [publicator.domain.aggregates.user :as user]
   [sibiro.core :as sibiro]))

(defn- post->model [routes post]
  {:id             (:id post)
   :url            (sibiro/path-for routes :post.list/handler)
   :edit-url       (sibiro/path-for routes :post.list/handler)
   :title          (:title post)
   :can-edit?      (::interactor/can-edit? post)
   :user-full-name (::user/full-name post)})

(defn processed [{:keys [routes]} posts]
  {:posts (map #(post->model routes %) posts)})
