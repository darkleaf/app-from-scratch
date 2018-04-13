(ns publicator.web.middlewares.layout
  (:require
   [publicator.web.template :as template]
   [publicator.web.presenters.layout :as presenters.layout]))

(defn wrap [handler]
  (fn [req]
    (let [resp (handler req)
          type (get-in resp [:headers "Content-Type"])
          body (:body resp)]
      (if (not= "text/html" type)
        resp
        (let [data (presenters.layout/present req)
              data (assoc data :content body)
              body (template/render "layout" data)]
          (assoc resp :body body))))))
