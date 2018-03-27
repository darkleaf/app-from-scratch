(ns publicator.use-cases.abstractions.user-queries
  (:require
   [clojure.spec.alpha :as s]
   [publicator.domain.aggregates.user :as user]))

(defprotocol GetByLogin
  (-get-by-login [this login]))

(declare ^:dynamic *get-by-login*)

(s/fdef get-by-login
        :args (s/cat :login ::user/login)
        :ret (s/nilable ::user/user))

(defn get-by-login [login]
  (-get-by-login *get-by-login* login))
