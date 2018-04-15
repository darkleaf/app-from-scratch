(ns publicator.domain.aggregates.user
  (:require
   [publicator.domain.abstractions.password-hasher :as password-hasher]
   [publicator.domain.abstractions.id-generator :as id-generator]
   [publicator.domain.abstractions.instant :as instant]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [clojure.spec.alpha :as s]))

(s/def ::id ::id-generator/id)
(s/def ::login (s/and string? #(re-matches #"\w{3,255}" %)))
(s/def ::full-name (s/and string? #(re-matches #".{2,255}" %)))
(s/def ::password (s/and string? #(re-matches #".{8,255}" %)))
(s/def ::password-digest ::password-hasher/encrypted)
(s/def ::posts-ids (s/coll-of ::id-generator/id :kind vector? :distinct true))
(s/def ::created-at inst?)
(s/def ::updated-at inst?)

(s/def ::user (s/keys :req-un [::id ::login ::full-name ::password-digest ::posts-ids
                               ::created-at ::updated-at]))

(defrecord User [id login full-name password-digest posts-ids created-at updated-at]
  aggregate/Aggregate
  (id [_] id)
  (spec [_] ::user)
  (wrap-update [this] (assoc this :updated-at (instant/now))))

(defn user? [x] (instance? User x))

(s/fdef build
        :args (s/cat :params (s/keys :req-un [::login ::full-name ::password]
                                     :opt-un [::posts-ids]))
        :ret ::user)

(defn build [{:keys [login full-name password posts-ids]
              :or   {posts-ids []}}]
  (map->User {:id              (id-generator/generate)
              :login           login
              :full-name       full-name
              :password-digest (password-hasher/derive password)
              :posts-ids       posts-ids
              :updated-at      (instant/now)
              :created-at      (instant/now)}))

(defn authenticated? [user password]
  (password-hasher/check password (:password-digest user)))
