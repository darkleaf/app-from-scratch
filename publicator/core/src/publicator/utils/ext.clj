(ns publicator.utils.ext)

(defn in? [coll elm]
  (boolean (some #(= elm %) coll)))

(defn map-vals [f m]
  (reduce-kv
   (fn [acc k v] (assoc acc k (f v)))
   {} m))

(defn reverse-merge [m1 m2]
  (merge m2 m1))
