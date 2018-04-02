(ns publicator.use-cases.interactors.user.log-out
  (:require
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.domain.aggregates.user :as user]
   [darkleaf.either :as e]
   [clojure.spec.alpha :as s]))

(defn- check-logged-in= []
  (if (user-session/logged-in?)
    (e/right)
    (e/left [::already-logged-out])))

(defn ^:dynamic *process* []
  @(e/let= [ok (check-logged-in=)]
     (user-session/log-out!)
     [::processed]))

(s/def ::already-logged-out (s/tuple #{::already-logged-out}))
(s/def ::processed (s/tuple #{::processed}))

(s/fdef process
        :ret (s/or :ok  ::processed
                   :err ::already-logged-out))

(defn process []
  (*process*))
