(ns publicator.use-cases.interactors.post.list
  (:require
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.use-cases.abstractions.post-queries :as post-q]
   [publicator.domain.services.user-posts :as user-posts]
   [clojure.spec.alpha :as s]))

(defn ^:dynamic *process* []
  (let [user  (user-session/user)
        posts (->> (post-q/get-list)
                   (map #(assoc % ::can-edit? (user-posts/author? user %))))]
    [::processed posts]))


(s/def ::can-edit? boolean?)
(s/def ::post (s/merge ::post-q/post
                       (s/keys :req [::can-edit?])))
(s/def ::posts (s/coll-of ::post))
(s/def ::processed (s/tuple #{::processed} ::posts))

(s/fdef process
        :ret (s/or :ok ::processed))

(defn process []
  (*process*))
