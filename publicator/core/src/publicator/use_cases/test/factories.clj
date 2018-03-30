(ns publicator.use-cases.test.factories
  (:require
   [publicator.domain.test.factories :as factories]
   [publicator.use-cases.abstractions.storage :as storage]))

(def gen factories/gen)

(defn create-user
  ([] (create-user {}))
  ([params]
   (storage/tx-create
    (factories/build-user params))))


(defn create-post
  ([] (create-post {}))
  ([params]
   (storage/tx-create
    (factories/build-post params))))
