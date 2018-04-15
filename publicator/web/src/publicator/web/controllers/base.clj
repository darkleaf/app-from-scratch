(ns publicator.web.controllers.base)

(defmulti handle (fn [result ctx] (first result)))

(defmethod handle ::forbidden [_ _]
  {:status 403
   :headers {}
   :body "forbidden"})

(defmethod handle ::not-found [_ _]
  {:status 404
   :headers {}
   :body "not-found"})
