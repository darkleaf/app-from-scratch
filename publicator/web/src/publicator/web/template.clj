(ns publicator.web.template
  (:require
   [cljstache.core :as mustache]))

(defn render [template-name data]
  (let [path (str "publicator/web/templates/" template-name ".mustache")]
    (mustache/render-resource path data)))
