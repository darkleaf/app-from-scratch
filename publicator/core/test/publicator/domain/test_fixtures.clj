(ns publicator.domain.test-fixtures
  (:require
   [orchestra.spec.test :as st]))

(defn instrument [f]
  (st/instrument)
  (f)
  (st/unstrument))
