(ns publicator.use-cases.interactors.post.show
  (:require
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.domain.services.user-posts :as user-posts]
   [publicator.use-cases.abstractions.post-queries :as post-q]
   [publicator.domain.aggregates.post :as post]
   [darkleaf.either :as e]
   [clojure.spec.alpha :as s]))

(defn- get-by-id= [id]
  (if-let [post (post-q/get-by-id id)]
    (e/right post)
    (e/left {:type ::not-found})))


(s/def ::can-edit? boolean?)
(s/def ::post (s/merge ::post-q/post
                       (s/keys :req [::can-edit?])))
(s/def ::type #{::processed ::not-found})

(s/fdef process
        :args (s/cat :id ::post/id)
        :ret (s/keys :req-un [::type ::post]))

(defn process [id]
  @(e/let= [user (user-session/user)
            post (get-by-id= id)
            post (assoc post ::can-edit? (user-posts/author? user post))]
     {:type ::processed, :post post}))
