(ns app.person
  (:require
   [clojure.test :as t]
   [clojure.spec.alpha :as s]
   [orchestra.spec.test :as st]))

(s/def ::id pos-int?)
(s/def ::name string?)
(s/def ::person (s/keys :req-un [::id ::name]))

(defrecord Person [id name])

;; begin abstract id generator
(defprotocol IdGenerator
  (-generate-id [this]))

(declare ^:dynamic *id-generator*)

(s/fdef generate-id
        :ret ::id)

(defn generate-id []
  (-generate-id *id-generator*))
;; end

(s/fdef build
        :args (s/cat :params (s/keys :req-un [::name]))
        :ret ::person)

(defn build [{:keys [name]}]
  (map->Person {:id (generate-id)
                :name name}))

(defn person? [x] (instance? Person x))

;; begin fake id generator
(deftype FakeIdGenerator [counter]
  IdGenerator

  (-generate-id [_]
    (swap! counter inc)))

(defn build-fake-id-generator []
  (FakeIdGenerator. (atom 0)))
;; end

;; begin tests

;; подменяем функции на вариант, проверяющий спецификацию
(st/instrument)

(t/use-fixtures :each (fn [test]
                        (binding [*id-generator* (build-fake-id-generator)]
                          (test))))

(t/deftest build-test
  (let [params {:name "Alice"}
        person (build params)]
    (t/is (person? person))))
;; end
