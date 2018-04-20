(ns publicator.web.url-helpers
  (:require
   [sibiro.core :as sibiro]))

;; defined in publicator.web.routing
;; prevent cyclic dependecy
(declare routes)

(defn uri-for [& args]
  (apply sibiro/uri-for routes args))

(defn path-for [& args]
  (apply sibiro/path-for routes args))
