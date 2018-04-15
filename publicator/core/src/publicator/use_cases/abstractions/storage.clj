(ns publicator.use-cases.abstractions.storage
  (:require
   [clojure.spec.alpha :as s]
   [publicator.domain.abstractions.id-generator :as id-generator]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [publicator.domain.identity :as identity]
   [publicator.utils.ext :as ext]))

(defprotocol Storage
  (wrap-tx [this body]))

(defprotocol Transaction
  (get-many [t ids])
  (create [t state]))

(s/fdef get-many
        :args (s/cat :tx any?
                     :ids (s/coll-of ::id-generator/id :distinct true))
        :ret (s/map-of ::id-generator/id ::identity/identity)
        :fn (fn identity-map? [{:keys [args ret]}]
              (= ret (apply get-many args))))

(s/fdef create
        :args (s/cat :tx any?
                     :state ::aggregate/aggregate)
        :ret ::identity/identity)

(declare ^:dynamic *storage*)

(defmacro with-tx
  "Note that body forms may be called multiple times,
   and thus should be free of side effects."
  [tx-name & body-forms-free-of-side-effects]
  `(wrap-tx *storage*
            (fn [~tx-name]
              ~@body-forms-free-of-side-effects)))

(s/fdef get-one
        :args (s/cat :tx any?
                     :id ::id-generator/id)
        :ret (s/nilable ::identity/identity))

(defn get-one [t id]
  (let [res (get-many t [id])]
    (get res id)))


(s/fdef tx-get-one
        :args (s/cat :id ::id-generator/id)
        :ret (s/nilable ::aggregate/aggregate))

(defn tx-get-one [id]
  (with-tx t
    (when-let [x (get-one t id)]
      @x)))


(s/fdef tx-get-many
        :args (s/cat :ids (s/coll-of ::id-generator/id :distinct true))
        :ret (s/map-of ::id-generator/id ::aggregate/aggregate))

(defn tx-get-many [ids]
  (with-tx t
    (->> ids
         (get-many t)
         (ext/map-vals deref))))

(s/fdef tx-create
        :args (s/cat :state ::aggregate/aggregate)
        :ret ::aggregate/aggregate
        :fn #(= (-> % :args :state)
                (-> % :ret)))

(defn tx-create [state]
  (with-tx t
    @(create t state)))


(s/fdef tx-alter
        :args (s/cat :id ::id-generator/id
                     :f fn?
                     :args (s/* any?))
        :ret (s/nilable ::aggregate/aggregate))

(defn tx-alter [id f & args]
  (with-tx t
    (when-let [x (get-one t id)]
      (dosync
       (apply identity/alter x f args)))))
