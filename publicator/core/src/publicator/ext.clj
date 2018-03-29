(ns publicator.ext
  (:require
   [clojure.spec.alpha :as s]))

;; https://groups.google.com/forum/#!topic/clojure/fti0eJdPQJ8
(defmacro only-keys [& {:keys [req req-un opt opt-un] :as args}]
  `(s/merge (s/keys ~@(apply concat (vec args)))
            (s/map-of ~(set (concat req
                                    (map (comp keyword name) req-un)
                                    opt
                                    (map (comp keyword name) opt-un)))
                      any?)))

(defn in? [coll elm]
  (boolean (some #(= elm %) coll)))

(defn map-vals [f m]
  (reduce-kv
   (fn [acc k v] (assoc acc k (f v)))
   {} m))

(defn reverse-merge [m1 m2]
  (merge m2 m1))
