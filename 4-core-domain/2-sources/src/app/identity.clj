(ns app.identity
  (:require
   [app.aggregate :as aggregate]
   [clojure.spec.alpha :as s])
  (:import
   [clojure.lang Ref]))

(s/def ::identity #(instance? Ref %))

(s/fdef build
  :args (s/cat :initial ::aggregate/aggregate)
  :ret ::identity)

(defn build [initial]
  (ref initial))
