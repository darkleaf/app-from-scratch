(ns publicator.use-cases.interactors.post.update
  (:require
   [publicator.domain.aggregates.post :as post]
   [publicator.domain.identity :as identity]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.domain.services.user-posts :as user-posts]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.utils.spec :as utils.spec]
   [darkleaf.either :as e]
   [clojure.spec.alpha :as s]))

(s/def ::params (utils.spec/only-keys :req-un [::post/title ::post/content]))

(defn- check-logged-in= []
  (if (user-session/logged-in?)
    (e/right)
    (e/left [::logged-out])))

(defn- check-params= [params]
  (if-some [ed (s/explain-data ::params params)]
    (e/left [::invalid-params ed])
    (e/right)))

(defn- check-authorization= [t post]
  (let [iuser (user-session/iuser t)]
    (if (user-posts/author? @iuser post)
      (e/right)
      (e/left [::not-authorized]))))

(defn- get-ipost= [t id]
  (if-some [ipost (storage/get-one t id)]
    (e/right ipost)
    (e/left [::not-found])))

(defn- update-post [ipost params]
  (dosync (identity/alter ipost merge params)))

(defn- post->params [post]
  (select-keys post [:title :content]))

(defn ^:dynamic *initial-params* [id]
  (storage/with-tx t
    @(e/let= [ok     (check-logged-in=)
              ipost  (get-ipost= t id)
              ok     (check-authorization= t @ipost)
              params (post->params @ipost)]
       [::initial-params params])))

(defn ^:dynamic *process* [id params]
  (storage/with-tx t
    @(e/let= [ok    (check-logged-in=)
              ok    (check-params= params)
              ipost (get-ipost= t id)
              ok    (check-authorization= t @ipost)]
       (update-post ipost params)
       [::processed @ipost])))

(s/def ::logged-out (s/tuple #{::logged-out}))
(s/def ::invalid-params (s/tuple #{::invalid-params} map?))
(s/def ::not-found (s/tuple #{::not-found}))
(s/def ::not-authorized (s/tuple #{::not-authorized}))
(s/def ::initial-params (s/tuple #{::initial-params} map?))
(s/def ::processed (s/tuple #{::processed} ::post/post))

(s/fdef initial-params
        :args (s/cat :id ::post/id)
        :ret (s/or :ok  ::initial-params
                   :err ::logged-out
                   :err ::not-authorized
                   :err ::not-found))

(s/fdef process
        :args (s/cat :id ::post/id
                     :params any?)
        :ret (s/or :ok  ::processed
                   :err ::logged-out
                   :err ::not-authorized
                   :err ::not-found
                   :err ::invalid-params))

(defn initial-params [id]
  (*initial-params* id))

(defn process [id params]
  (*process* id params))
