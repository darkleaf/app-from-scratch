(ns publicator.utils.fixtures
  (:require
   [orchestra.spec.test :as st]))

(defn instrument [f]
  (st/instrument)
  (f))
