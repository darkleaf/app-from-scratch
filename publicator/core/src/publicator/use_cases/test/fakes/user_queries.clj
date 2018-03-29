(ns publicator.use-cases.test.fakes.user-queries
  (:require
   [publicator.domain.aggregates.user :as user]
   [publicator.use-cases.abstractions.user-queries :as user-q]))

(deftype GetByLogin [db]
  user-q/GetByLogin
  (-get-by-login [_ login]
    (->> db
         (deref)
         (vals)
         (filter user/user?)
         (filter #(= login (:login %)))
         (first))))

(defn binding-map [db]
  {#'user-q/*get-by-login* (->GetByLogin db)})
