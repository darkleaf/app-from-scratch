(ns publicator.domain.aggregates.post
  (:require
   [clojure.spec.alpha :as s]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [publicator.domain.abstractions.id-generator :as id-generator]
   [publicator.domain.abstractions.instant :as instant]))

(s/def ::id ::id-generator/id)
(s/def ::title (s/and string? #(re-matches #".{1,255}" %)))
(s/def ::content string?)
(s/def ::created-at inst?)
(s/def ::updated-at inst?)

(s/def ::post (s/keys :req-un [::id ::title ::content ::created-at ::updated-at]))

(defrecord Post [id title content created-at updated-at]
  aggregate/Aggregate
  (id [_] id)
  (spec [_] ::post)
  (wrap-update [this] (assoc this :updated-at (instant/now))))

(defn post? [x] (instance? Post x))

(s/fdef build
        :args (s/cat :params (s/keys :req-un [::title ::content]))
        :ret ::post)

(defn build [{:keys [title content]}]
  (map->Post {:id (id-generator/generate)
              :title title
              :content content
              :created-at (instant/now)
              :updated-at (instant/now)}))
