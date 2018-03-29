(ns publicator.use-cases.abstractions.post-queries)
;;   (:require
;;    [publicator.domain.user :as user]
;;    [publicator.domain.post :as post]
;;    [clojure.spec.alpha :as s]))

;; (defprotocol GetList
;;   (-get-list [this]))

;; (declare ^:dynamic *get-list*)

;; (s/def ::author-full-name ::user/full-name)
;; (s/def ::list-item (s/keys :req-un [::post/id ::post/title
;;                                     ::post/author-id ::author-full-name]))
;; (s/def ::list (s/coll-of ::list-item))

;; (defn get-list []
;;   {:post [(s/assert ::list %)]}
;;   (-get-list *get-list*))
