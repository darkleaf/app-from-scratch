(ns darkleaf.either)

(defprotocol Either
  (left? [this])
  (right? [this])
  (invert [this])
  (-bimap [this leftf rightf]))

(declare left)
(declare right)

(deftype Left [val]
  clojure.lang.IDeref
  (deref [_] val)
  Either
  (left? [_] true)
  (right? [_] false)
  (invert [_] (right val))
  (-bimap [_ leftf _] (-> val leftf left)))

(deftype Right [val]
  clojure.lang.IDeref
  (deref [_] val)
  Either
  (left? [_] false)
  (right? [_] true)
  (invert [_] (left val))
  (-bimap [_ _ rightf] (-> val rightf right)))

(defn left
  ([] (left nil))
  ([x] (->Left x)))

(defn right
  ([] (right nil))
  ([x] (->Right x)))

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

(defmethod print-method Left [v ^java.io.Writer w]
  (doto w
    (.write "#<Left ")
    (.write (pr-str @v))
    (.write ">")))

(defmethod print-method Right [v ^java.io.Writer w]
  (doto w
    (.write "#<Right ")
    (.write (pr-str @v))
    (.write ">")))

(defmacro let= [bindings & body]
  (assert (-> bindings count even?))
  (if (empty? bindings)
    `(let [res# (do ~@body)]
       (assert (either? res#))
       res#)
    (let [[name expr & bindings] bindings]
      `(let [val# ~expr]
         (assert (either? val#))
         (if (left? val#)
           val#
           (let [~name @val#]
             (let= [~@bindings] ~@body)))))))

(defn >>=
  ([mv f=] (let= [v mv] (f= v)))
  ([mv f= & fs=] (reduce >>= mv (cons f= fs=))))

(defmacro >> [& mvs]
  (assert (seq mvs))
  (let [val (gensym "val")]
    `(let= [~@(interleave (repeat val) mvs)]
       (right ~val))))
