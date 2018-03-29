(ns publicator.use-cases.interactors.user.log-in
  (:require
   [publicator.use-cases.abstractions.user-queries :as user-q]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.domain.aggregates.user :as user]
   [darkleaf.either :as e]
   [clojure.spec.alpha :as s]
   [publicator.ext :as ext]))

(s/def ::params (ext/only-keys :req-un [::user/login ::user/password]))

(defn- check-logged-out= []
  (if (user-session/logged-out?)
    (e/right)
    (e/left {:type ::already-logged-in})))

(defn- find-user= [params]
  (if-let [user (user-q/get-by-login (:login params))]
    (e/right user)
    (e/left {:type ::authentication-failed})))

(defn- check-authentication= [user params]
  (if (user/authenticated? user (:password params))
    (e/right)
    (e/left {:type ::authentication-failed})))

(defn- check-params= [params]
  (if-let [exp (s/explain-data ::params params)]
    (e/left {:type ::invalid-params, :explain-data exp})
    (e/right)))

(defn initial-params []
  @(e/let= [ok (check-logged-out=)]
     (e/right {:type ::initial-params, :initial-params {}})))

(defn process [params]
  @(e/let= [ok   (check-logged-out=)
            ok   (check-params= params)
            user (find-user= params)
            ok   (check-authentication= user params)]
     (user-session/log-in! user)
     (e/right {:type ::processed :user user})))
