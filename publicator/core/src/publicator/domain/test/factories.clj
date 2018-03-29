(ns publicator.domain.test.factories
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sgen]
   [publicator.domain.aggregates.user :as user]
   [publicator.domain.aggregates.post :as post]))

(defn gen [spec]
  (-> spec s/gen sgen/generate))

(defn build-user
  ([] (build-user {}))
  ([params]
   (-> (s/keys :req-un [::user/login
                        ::user/full-name
                        ::user/password])
       gen
       (merge params)
       user/build)))

(defn build-post
  ([] (build-post {}))
  ([params]
   (-> (s/keys :req-un [::post/title
                        ::post/content])
       gen
       (merge params)
       post/build)))
