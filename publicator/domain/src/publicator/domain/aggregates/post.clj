(ns publicator.domain.aggregates.post
  (:require
   [clojure.spec.alpha :as s]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [publicator.domain.abstractions.id-generator :as id-generator]))

(s/def ::id ::id-generator/id)
(s/def ::title (s/and string? #(re-matches #".{1,255}" %)))
(s/def ::content string?)

(s/def ::post (s/keys :req-un [::id ::title ::content]))

(defrecord Post [id title content]
  aggregate/Aggregate
  (id [_] id)
  (valid? [this] (s/valid? ::post this)))


(s/fdef build
        :args (s/cat :params (s/keys :req-un [::title ::content]))
        :ret ::post)

(defn build [{:keys [title content]}]
  (let [id (id-generator/generate)]
    (->Post id title content)))


(defn post? [x] (instance? Post x))
