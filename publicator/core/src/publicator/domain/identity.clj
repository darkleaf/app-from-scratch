(ns publicator.domain.identity
  (:refer-clojure :exclude [alter])
  (:require
   [clojure.core :as core]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [clojure.spec.alpha :as s])
  (:import
   [clojure.lang Ref]))

(defn- build-validator [initial]
  (fn [new]
    (if (not= (class initial)
              (class new))
      (throw (ex-info "Aggregate class was changed."
                      {::type    :class-was-changed
                       ::initial initial
                       ::new     new})))
    (if (not= (aggregate/id initial)
              (aggregate/id new))
      (throw (ex-info "Aggregate id was changed."
                      {::type    :id-was-changed
                       ::initial initial
                       ::new     new})))
    (if-let [ed (s/explain-data (aggregate/spec new) new)]
      (throw (ex-info (str "Aggregate was invalid. "
                           (with-out-str (s/explain-out ed)))
                      {::type         :aggregate-was-invalid
                       ::explain-data ed})))
    true))

(s/def ::identity (s/and #(instance? Ref %)
                         #(-> % meta ::identity true?)))

(defn build [initial]
  (ref initial
       :meta {::identity true}
       :validator (build-validator initial)))

(s/fdef alter
        :args (s/cat :identity ::identity
                     :f ifn?
                     :args (s/* any?))
        :ret ::aggregate/aggregate)

(defn alter [identity f & args]
  (let [updater (comp aggregate/wrap-update f)]
    (apply core/alter identity updater args)))
