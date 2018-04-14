(ns publicator.web.routing
  (:require
   [sibiro.core :as sibiro]
   [clojure.set :as set]
   [publicator.web.controllers.pages.root :as pages.root]
   [publicator.web.controllers.user.log-in :as user.log-in]))

(defn build []
  (sibiro/compile-routes
   (set/union
    pages.root/routes
    user.log-in/routes)))
