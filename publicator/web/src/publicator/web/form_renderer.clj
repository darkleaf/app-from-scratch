(ns publicator.web.form-renderer
  (:require
   [publicator.web.transit :as transit]))

(defn render [form]
  (str "<div data-form-ujs='" (transit/write form)  "'/>"))
