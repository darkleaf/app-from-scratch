(ns publicator.use-cases.test.fakes.storage
  (:require
   [publicator.domain.identity :as identity]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [publicator.use-cases.abstractions.storage :as storage]))

(deftype Transaction [db]
  storage/Transaction
  (get-many [_ ids]
    (select-keys @db ids))

  (create [_ state]
    (let [id     (aggregate/id state)
          istate (identity/build state)]
      (alter db assoc id istate)
      istate)))

(deftype Storage [db]
  storage/Storage
  (wrap-tx [_ body]
    (let [t (Transaction. db)]
      (body t))))

(defn build-db []
  (ref {}))

(defn binding-map [db]
  {#'storage/*storage* (->Storage db)})
