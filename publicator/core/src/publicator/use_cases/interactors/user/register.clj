(ns publicator.use-cases.interactors.user.register
  (:require
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.use-cases.abstractions.user-queries :as user-q]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.domain.aggregates.user :as user]
   [darkleaf.either :as e]
   [clojure.spec.alpha :as s]
   [publicator.utils.spec :as utils.spec]))

(s/def ::params (utils.spec/only-keys :req-un [::user/login
                                               ::user/full-name
                                               ::user/password]))

(defn- check-logged-out= []
  (if (user-session/logged-out?)
    (e/right)
    (e/left [::already-logged-in])))

(defn- check-params= [params]
  (if-let [exp (s/explain-data ::params params)]
    (e/left [::invalid-params exp])
    (e/right)))

(defn- check-not-registered= [params]
  (if (user-q/get-by-login (:login params))
    (e/left [::already-registered])
    (e/right)))

(defn- create-user [params]
  (storage/tx-create (user/build params)))

(defn ^:dynamic *initial-params* []
  @(e/let= [ok (check-logged-out=)]
     [::initial-params {}]))

(defn ^:dynamic *process* [params]
  @(e/let= [ok   (check-logged-out=)
            ok   (check-params= params)
            ok   (check-not-registered= params)
            user (create-user params)]
     (user-session/log-in! user)
     [::processed user]))

(s/def ::already-logged-in (s/tuple #{::already-logged-in}))
(s/def ::invalid-params (s/tuple #{::invalid-params} map?))
(s/def ::already-registered (s/tuple #{::already-registered}))
(s/def ::initial-params (s/tuple #{::initial-params} map?))
(s/def ::processed (s/tuple #{::processed} ::user/user))

(s/fdef initial-params
        :ret (s/or :ok  ::initial-params
                   :err ::already-logged-in))

(s/fdef process
        :args (s/cat :params any?)
        :ret (s/or :ok  ::processed
                   :err ::already-logged-in
                   :err ::invalid-params
                   :err ::already-registered))

(defn initial-params []
  (*initial-params*))

(defn process [params]
  (*process* params))
