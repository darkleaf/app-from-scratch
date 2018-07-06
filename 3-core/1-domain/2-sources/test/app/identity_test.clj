(ns app.identity-test
  (:require
   [app.identity :as sut]
   [app.aggregate :as aggregate]
   [app.post :as post]
   [orchestra.spec.test :as st]
   [clojure.test :as t]))

(defn- instrument-fixture [t]
  (st/instrument)
  (t))

(t/use-fixtures :once instrument-fixture)

(t/deftest identity-test
  (let [post (post/map->Post {:id       1
                              :title    "Lorem ipsum"
                              :content  "Some text"
                              :comments []})]
    (t/testing "change id"
      (let [ipost (sut/build post)]
        (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                                #"Aggregate id was changed."
                                (dosync (alter ipost assoc :id 2))))))
    (t/testing "change class"
      (let [ipost (sut/build post)]
        (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                                #"Aggregate class was changed."
                                (dosync (ref-set ipost (reify
                                                         aggregate/Aggregate
                                                         (id [_] 42)
                                                         (spec [_] any?))))))))
    (t/testing "invalid"
      (let [ipost (sut/build post)]
        (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                                #"Aggregate was invalid."
                                (dosync
                                 (alter ipost
                                        update :comments
                                        conj {:content "Awesome post!"}))))))))
