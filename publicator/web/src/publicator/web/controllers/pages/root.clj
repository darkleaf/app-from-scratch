(ns publicator.web.controllers.pages.root
  (:require
   [ring.util.http-response :as http-response]
   [publicator.web.template :as template]))

(defn show [req]
  (-> (template/render "pages/root" {})
      (http-response/ok)
      (http-response/content-type "text/html")))

(def routes
  #{[:get "/" #'show :pages/root]})
