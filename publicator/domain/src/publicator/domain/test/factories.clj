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
   (let [params (-> `user/build
                    s/spec
                    :args
                    gen
                    vec
                    (update 0 merge params))]
     (apply user/build params))))

(defn build-post
  ([] (build-post {}))
  ([params]
   (let [params (-> `post/build
                    s/spec
                    :args
                    gen
                    vec
                    (update 0 merge params))]
     (apply post/build params))))
