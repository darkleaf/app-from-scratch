(ns publicator.use-cases.interactors.post.create
  (:require
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.domain.aggregates.post :as post]
   [clojure.spec.alpha :as s]
   [publicator.use-cases.utils.spec :as utils.spec]
   [darkleaf.either :as e]))

(s/def ::params (utils.spec/only-keys :req-un [::post/title ::post/content]))

(defn- check-logged-in= []
  (if (user-session/logged-in?)
    (e/right)
    (e/left {:type ::logged-out})))

(defn- check-params= [params]
  (if-let [ed (s/explain-data ::params params)]
    (e/left {:type ::invalid-params, :explain-data ed})
    (e/right)))

(defn- create-post= [t params]
  (e/right
   (storage/create t (post/build params))))

(defn- set-authorship [t ipost]
  (let [user-id (user-session/user-id)
        post-id (:id @ipost)
        iuser   (storage/get-one t user-id)]
    (alter iuser update :posts-ids conj post-id)))

(defn initial-params []
  @(e/let= [ok (check-logged-in=)]
     (e/right {:type ::initial-params, :initial-params {}})))

(defn process [params]
  (storage/with-tx t
    @(e/let= [ok (check-logged-in=)
              ok (check-params= params)
              ipost (create-post= t params)]
       (set-authorship t ipost)
       (e/right {:type ::processed, :post @ipost}))))
