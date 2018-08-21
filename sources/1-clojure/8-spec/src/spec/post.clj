(ns spec.post
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sgen]
   [clojure.spec.test.alpha :as st]))

(s/def ::id int?)
(s/def ::title (s/and string? #(re-matches #".{1,255}" %)))
(s/def ::content string?)
(s/def ::created-at inst?)

(s/def ::post (s/keys :req-un [::id ::title ::content ::created-at]))

(s/fdef build
  :args (s/cat :params (s/keys :req-un [::title ::content]))
  :ret ::post)

(let [counter (atom 0)]
  (defn build [{:keys [title content]}]
    {:id         (swap! counter inc)
     :title      title
     :content    content
     :created-at (java.util.Date.)}))

(comment
  (s/valid? ::post {}) ;; #=> false

  ;; returned data:
  ;; #:clojure.spec.alpha{:problems
  ;;                      ({:path [],
  ;;                        :pred (clojure.core/fn [%]
  ;;                                (clojure.core/contains? % :content)),
  ;;                        :val {:id 1, :title "", :created-at 2018},
  ;;                        :via [:spec.post/post],
  ;;                        :in []}
  ;;                       {:path [:title],
  ;;                        :pred (clojure.core/fn [%]
  ;;                                (clojure.core/re-matches #".{1,255}" %)),
  ;;                        :val "",
  ;;                        :via [:spec.post/post :spec.post/title],
  ;;                        :in [:title]}
  ;;                       {:path [:created-at],
  ;;                        :pred clojure.core/inst?,
  ;;                        :val 2018,
  ;;                        :via [:spec.post/post :spec.post/created-at],
  ;;                        :in [:created-at]}),
  ;;                      :spec :spec.post/post,
  ;;                      :value {:id 1, :title "", :created-at 2018}}
  (s/explain-data ::post {:id 1, :title "", :created-at 2018}))

(comment
  (-> ::post
      s/gen
      sgen/generate))

(comment
  (build {:title "Hello"})

  (st/instrument)

  ;; Boom!
  ;; Spec assertion failed
  ;; Problems:
  ;;      val: {:title "Hello"}
  ;;       in: [0]
  ;;   failed: (contains? % :content)
  ;;       at: [:args :params])
  (build {:title "Hello"}))
