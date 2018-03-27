(ns publicator.use-cases.interactors.user.register
  (:require
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.use-cases.abstractions.user-queries :as user-q]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.domain.aggregates.user :as user]
   [darkleaf.either :as e]
   [clojure.spec.alpha :as s]))

(s/def ::params (s/keys :req-un [::user/login
                                 ::user/full-name
                                 ::user/password]))

(defn- check-logged-out= []
  (if (user-session/logged-out?)
    (e/right)
    (e/left {:type ::already-logged-in})))

(defn- check-params= [params]
  (if-let [exp (s/explain-data ::params params)]
    (e/left {:type ::invalid-params, :explain-data exp})
    (e/right)))

(defn- create-user= [params]
  (if (user-q/get-by-login (:login params))
    (e/left {:type ::already-registered})
    (e/right (storage/tx-create (user/build params)))))

(defn initial-params []
  @(e/let= [ok (check-logged-out=)]
     (e/right {:type ::initial-params, :initial-params {}})))

(defn process [params]
  @(e/let= [ok   (check-logged-out=)
            ok   (check-params= params)
            user (create-user= params)]
     (user-session/log-in! user)
     (e/right {:type ::processed :user user})))
