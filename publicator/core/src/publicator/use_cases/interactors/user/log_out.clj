(ns publicator.use-cases.interactors.user.log-out
  (:require
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.domain.aggregates.user :as user]
   [darkleaf.either :as e]
   [clojure.spec.alpha :as s]))

(defn- check-logged-in= []
  (if (user-session/logged-in?)
    (e/right)
    (e/left {:type ::already-logged-out})))

(defn process []
  @(e/let= [ok (check-logged-in=)]
     (user-session/log-out!)
     {:type ::processed}))
