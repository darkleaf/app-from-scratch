(ns publicator.domain.services.user-posts
  (:require
   [publicator.domain.aggregates.user :as user]
   [publicator.domain.aggregates.post :as post]
   [publicator.utils.ext :as ext]
   [clojure.spec.alpha :as s]))

(s/fdef add-post
        :args (s/cat :user ::user/user
                     :post ::post/post)
        :ret ::user/user)

(defn add-post [user post]
  (update user :posts-ids conj (:id post)))


(s/fdef author?
        :args (s/cat :user (s/nilable ::user/user)
                     :post (s/nilable ::post/post))
        :ret boolean?)

(defn author? [user post]
  (ext/in? (:posts-ids user) (:id post)))
