(ns publicator.use-cases.abstractions.post-queries
  (:require
   [publicator.domain.aggregates.user :as user]
   [publicator.domain.aggregates.post :as post]
   [clojure.spec.alpha :as s]))

(defprotocol GetList
  (-get-list [this]))

(declare ^:dynamic *get-list*)

(s/def ::post (s/merge ::post/post
                       (s/keys :req [::user/id ::user/full-name])))

(s/fdef get-list
        :ret (s/coll-of ::post))

(defn get-list []
  (-get-list *get-list*))


(defprotocol GetById
  (-get-by-id [this id]))

(declare ^:dynamic *get-by-id*)

(s/fdef get-by-id
        :args (s/cat :id ::post/id)
        :ret (s/nilable ::post))

(defn get-by-id [id]
  (-get-by-id *get-by-id* id))
