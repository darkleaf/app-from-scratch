(ns publicator.use-cases.test.fixtures
  (:require
   [publicator.domain.test.fixtures :as fixtures]
   [publicator.use-cases.test.fakes.session :as session]
   [publicator.use-cases.test.fakes.storage :as storage]
   [publicator.use-cases.test.fakes.user-queries :as user-q]
   [publicator.use-cases.test.fakes.post-queries :as post-q]))

(defn fakes [f]
  (let [db          (storage/build-db)
        binding-map (merge (session/binding-map)
                           (storage/binding-map db)
                           (user-q/binding-map db)
                           (post-q/binding-map db))]
    (with-bindings binding-map
      (fixtures/fakes f))))
