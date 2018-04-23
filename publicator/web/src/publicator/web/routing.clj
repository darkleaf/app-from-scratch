(ns publicator.web.routing
  (:require
   [sibiro.core :as sibiro]
   [clojure.set :as set]
   [publicator.web.controllers.pages.root :as pages.root]
   [publicator.web.controllers.user.log-in :as user.log-in]
   [publicator.web.controllers.user.log-out :as user.log-out]
   [publicator.web.controllers.user.register :as user.register]
   [publicator.web.controllers.post.list :as post.list]
   [publicator.web.controllers.post.show :as post.show]
   [publicator.web.controllers.post.create :as post.create]
   [publicator.web.controllers.post.update :as post.update]
   [publicator.web.url-helpers :as url-helpers]))

(def routes
  (sibiro/compile-routes
   (set/union
    pages.root/routes
    user.log-in/routes
    user.log-out/routes
    user.register/routes
    post.list/routes
    post.show/routes
    post.create/routes
    post.update/routes)))

(alter-var-root #'url-helpers/routes (constantly routes))
