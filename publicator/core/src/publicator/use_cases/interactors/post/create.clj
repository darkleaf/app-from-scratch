(ns publicator.use-cases.interactors.post.create
  (:require
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.domain.aggregates.post :as post]
   [publicator.domain.services.user-posts :as user-posts]
   [clojure.spec.alpha :as s]
   [publicator.ext :as ext]
   [darkleaf.either :as e]))

(s/def ::params (ext/only-keys :req-un [::post/title ::post/content]))

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
        iuser   (storage/get-one t user-id)]
    (alter iuser user-posts/add-post @ipost)))

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
