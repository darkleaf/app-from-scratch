(ns publicator.domain.abstractions.instant
  (:import
   [java.time Instant]))

(defn ^:dynamic now []
  (Instant/now))
