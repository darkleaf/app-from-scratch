(ns app.post
  (:require
   [app.aggregate :as aggregate]
   [app.post.comment :as comment]
   [clojure.spec.alpha :as s]))

(s/def ::id pos-int?)
(s/def ::title string?)
(s/def ::content string?)
(s/def ::comments (s/coll-of ::comment/comment :kind vector?))
(s/def ::post (s/keys :req-un [::id ::title ::content ::comments]))

(defrecord Post [id title content comments]
  aggregate/Aggregate
  (id [_] id)
  (spec [_] ::post))
