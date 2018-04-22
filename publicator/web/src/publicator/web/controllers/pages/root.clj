(ns publicator.web.controllers.pages.root
  (:require
   [ring.util.http-response :as http-response]
   [publicator.web.controllers.base :as base]))

(defn show [req]
  (base/render "pages/root" {}))

(def routes
  #{[:get "/" #'show :pages/root]})
