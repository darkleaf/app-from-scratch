(ns app.post.comment
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::content string?)
(s/def ::author string?)
(s/def ::comment (s/keys :req-un [::content ::author]))
