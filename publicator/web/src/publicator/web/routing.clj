(ns publicator.web.routing
  (:require
   [sibiro.core :as sibiro]
   [clojure.set :as set]
   [publicator.web.controllers.pages.root :as pages.root]))

(defn build []
  (sibiro/compile-routes
   (set/union
    pages.root/routes)))
