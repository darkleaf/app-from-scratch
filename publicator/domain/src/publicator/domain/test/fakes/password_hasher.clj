(ns publicator.domain.test.fakes.password-hasher
  (:require
   [publicator.domain.abstractions.password-hasher :as password-hasher]
   [clojure.string :as str]))

(deftype PasswordHasher []
  password-hasher/PasswordHasher

  (-derive [_ password]
    (str/reverse password))

  (-check [_ attempt encrypted]
    (= (str/reverse attempt)
       encrypted)))

(defn binding-map []
  {#'password-hasher/*password-hasher* (->PasswordHasher)})
