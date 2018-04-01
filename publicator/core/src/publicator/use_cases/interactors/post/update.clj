(ns publicator.use-cases.interactors.post.update
  (:require
   [publicator.domain.aggregates.post :as post]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.domain.services.user-posts :as user-posts]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.ext :as ext]
   [darkleaf.either :as e]
   [clojure.spec.alpha :as s]))

(s/def ::params (ext/only-keys :req-un [::post/title ::post/content]))

(defn- check-logged-in= []
  (if (user-session/logged-in?)
    (e/right)
    (e/left {:type ::logged-out})))

(defn- check-params= [params]
  (if-some [ed (s/explain-data ::params params)]
    (e/left {:type ::invalid-params, :explain-data ed})
    (e/right)))

(defn- check-authorization= [t post]
  (let [iuser (user-session/iuser t)]
    (if (user-posts/author? @iuser post)
      (e/right)
      (e/left {:type ::not-authorized}))))

(defn- get-ipost= [t id]
  (if-some [ipost (storage/get-one t id)]
    (e/right ipost)
    (e/left {:type ::not-found})))

(defn- update-post [ipost params]
  (dosync (alter ipost merge params)))

(defn- post->params [post]
  (select-keys post [:title :content]))

(defn initial-params [id]
  (storage/with-tx t
    @(e/let= [ok     (check-logged-in=)
              ipost  (get-ipost= t id)
              ok     (check-authorization= t @ipost)
              params (post->params @ipost)]
       {:type ::initial-params, :initial-params params})))

(defn process [id params]
  (storage/with-tx t
    @(e/let= [ok    (check-logged-in=)
              ok    (check-params= params)
              ipost (get-ipost= t id)
              ok    (check-authorization= t @ipost)]
       (update-post ipost params)
       {:type ::processed, :post @ipost})))
