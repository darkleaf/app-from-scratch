(ns publicator.domain.abstractions.password-hasher
  (:refer-clojure :exclude [derive])
  (:require [clojure.spec.alpha :as s]))

;; check нужет, т.к. derive для одного и того же пароля может давать разные результаты,
;; т.к. результат может содержать случайную соль

(defprotocol PasswordHasher
  (-derive [this password])
  (-check [this attempt encrypted]))

(declare ^:dynamic *password-hasher*)

(s/def ::password string?)
(s/def ::encrypted string?)

(s/fdef derive
        :args (s/cat :password ::password)
        :ret ::encrypted
        :fn #(not= (-> % :args :password)
                   (-> % :ret)))

(defn derive [password]
  (-derive *password-hasher* password))


(s/fdef check
        :args (s/cat :attempt ::password
                     :encrypted ::encrypted)
        :ret boolean?)

(defn check [attempt encrypted]
  (-check *password-hasher* attempt encrypted))
