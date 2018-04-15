(ns publicator.use-cases.interactors.post.create
  (:require
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.domain.aggregates.post :as post]
   [publicator.domain.services.user-posts :as user-posts]
   [publicator.domain.identity :as identity]
   [clojure.spec.alpha :as s]
   [publicator.utils.spec :as utils.spec]
   [darkleaf.either :as e]))

(s/def ::params (utils.spec/only-keys :req-un [::post/title ::post/content]))

(defn- check-logged-in= []
  (if (user-session/logged-in?)
    (e/right)
    (e/left [::logged-out])))

(defn- check-params= [params]
  (if-let [ed (s/explain-data ::params params)]
    (e/left [::invalid-params ed])
    (e/right)))

(defn- create-post [t params]
  (storage/create t (post/build params)))

(defn- set-authorship [t ipost]
  (let [iuser (user-session/iuser t)]
    (dosync (identity/alter iuser user-posts/add-post @ipost))))

(defn ^:dynamic *initial-params* []
  @(e/let= [ok (check-logged-in=)]
     [::initial-params {}]))

(defn ^:dynamic *process* [params]
  (storage/with-tx t
    @(e/let= [ok (check-logged-in=)
              ok (check-params= params)
              ipost (create-post t params)]
       (set-authorship t ipost)
       [::processed @ipost])))


(s/def ::logged-out (s/tuple #{::logged-out}))
(s/def ::invalid-params (s/tuple #{::invalid-params} map?))
(s/def ::initial-params (s/tuple #{::initial-params} map?))
(s/def ::processed (s/tuple #{::processed} ::post/post))

(s/fdef initial-params
        :ret (s/or :ok  ::initial-params
                   :err ::logged-out))

(s/fdef process
        :ret (s/or :ok  ::processed
                   :err ::logged-out
                   :err ::invalid-params))

(defn initial-params []
  (*initial-params*))

(defn process [params]
  (*process* params))
