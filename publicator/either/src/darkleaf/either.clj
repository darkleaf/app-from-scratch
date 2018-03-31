(ns darkleaf.either
  (:require
   [clojure.string :as str]))

(defprotocol Either
  (left? [this])
  (right? [this])
  (invert [this])
  (-bimap [this leftf rightf]))

(alter-meta! #'Either assoc :private true)
(alter-meta! #'-bimap assoc :private true)

(declare left)
(declare right)

(defmacro ^:private defeither [type-name val-name & body]
  (let [generated-constructor (symbol (str "->" type-name))
        constructor           (symbol (str/lower-case type-name))]
    `(do
       (deftype ~type-name [~val-name]
         Object
         (equals [this# other#]
           (and
            (= (class this#) (class other#))
            (= ~val-name (. other# ~val-name))))
         (hashCode [_] (hash ~val-name))

         clojure.lang.IHashEq
         (hasheq [_] (hash ~val-name))

         clojure.lang.IDeref
         (deref [_] ~val-name)

         Either
         ~@body)

       (alter-meta! (var ~generated-constructor)
                    assoc :private true)

       (defn ~constructor
         ([] (~constructor nil))
         ([x#] (~generated-constructor x#)))

       (defmethod print-method ~type-name [v# ^java.io.Writer w#]
         (doto w#
           (.write "#<")
           (.write ~(str type-name))
           (.write " ")
           (.write (pr-str @v#))
           (.write ">"))))))

(defeither Left value
  (left? [_] true)
  (right? [_] false)
  (invert [_] (right value))
  (-bimap [_ leftf _] (-> value leftf left)))

(defeither Right value
  (left? [_] false)
  (right? [_] true)
  (invert [_] (left value))
  (-bimap [_ _ rightf] (-> value rightf right)))

;; потому, что это по определению может быть только 2 обрертки
(defn either? [x]
  (or (instance? Left x)
      (instance? Right x)))

(defn bimap [leftf rightf mv]
  (-bimap mv leftf rightf))

;; не объявлен в протоколе, т.к. это частный случай bimap
(defn map-left [f mv]
  (bimap f identity mv))

;; не объявлен в протоколе, т.к. это частный случай bimap
(defn map-right [f mv]
  (bimap identity f mv))

(defmacro let= [bindings & body]
  (assert (-> bindings count even?))
  (if (empty? bindings)
    `(let [res# (do ~@body)]
       (if (either? res#)
         res#
         (right res#)))
    (let [[name expr & bindings] bindings]
      `(let [val# ~expr]
         (if (and (either? val#) (left? val#))
           val#
           (let [~name (if (either? val#)
                         @val#
                         val#)]
             (let= [~@bindings] ~@body)))))))

(defn >>=
  ([mv f=] (let= [v mv] (f= v)))
  ([mv f= & fs=] (reduce >>= mv (cons f= fs=))))

(defmacro >> [& mvs]
  (assert (seq mvs))
  (let [val (gensym "val")]
    `(let= [~@(interleave (repeat val) mvs)]
       (right ~val))))
