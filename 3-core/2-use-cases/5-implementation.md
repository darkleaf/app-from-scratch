**DRAFT**


Не все интеракторы имеют эти 3 функции, часто достаточно одного `process`.

Операция разрешена, если в текущей сессии пользователь разлогинен.

Форма может содержать предварительно заполненые поля,
например, регион проживания, опредленный по ip адресу.

При обработке формы мы должны выполнить следующие шаги:

+ проверить, разрешена ли операция
+ провалидировать входные параметры
+ проверить, что пользователь не зарегистриован
+ создать и сохранить пользователя
+ залогинить пользователя
+ вернуть пользователя














Ниже представлен код, который релизует эти 2 интерактора.
За них отвечают функции `initial-params` и `process`.

```clojure
(ns publicator.use-cases.interactors.user.register
  (:require
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.use-cases.abstractions.user-queries :as user-q]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.domain.aggregates.user :as user]
   [darkleaf.either :as e]
   [clojure.spec.alpha :as s]
   [publicator.utils.spec :as utils.spec]))

(s/def ::params (utils.spec/only-keys :req-un [::user/login
                                               ::user/full-name
                                               ::user/password]))

(defn- check-authorization= []
  (if (user-session/logged-in?)
    (e/left [::already-logged-in])
    (e/right [::authorized])))

(defn- check-params= [params]
  (if-let [exp (s/explain-data ::params params)]
    (e/left [::invalid-params exp])))

(defn- check-not-registered= [params]
  (if (user-q/get-by-login (:login params))
    (e/left [::already-registered])))

(defn- create-user [params]
  (storage/tx-create (user/build params)))

(defn initial-params []
  (e/extract
   (e/let= [ok (check-authorization=)]
     [::initial-params {}])))

(defn process [params]
  (e/extract
   (e/let= [ok   (check-authorization=)
            ok   (check-params= params)
            ok   (check-not-registered= params)
            user (create-user params)]
     (user-session/log-in! user)
     [::processed user])))

(defn authorize []
  (e/extract
   (check-authorization=)))

(s/def ::already-logged-in (s/tuple #{::already-logged-in}))
(s/def ::invalid-params (s/tuple #{::invalid-params} map?))
(s/def ::already-registered (s/tuple #{::already-registered}))
(s/def ::initial-params (s/tuple #{::initial-params} map?))
(s/def ::processed (s/tuple #{::processed} ::user/user))
(s/def ::authorized (s/tuple #{::authorized}))

(s/fdef initial-params
  :args nil?
  :ret (s/or :ok  ::initial-params
             :err ::already-logged-in))

(s/fdef process
  :args (s/cat :params any?)
  :ret (s/or :ok  ::processed
             :err ::already-logged-in
             :err ::invalid-params
             :err ::already-registered))

(s/fdef authorize
  :args nil?
  :ret (s/or :ok  ::authorized
             :err ::already-logged-in))
```

Как видно, код полностью соответствует словестному описанию.

Интерактор использует монаду either для реализации вычислений, которые могут окончиться неудачей.
Мы уже реализовывали ее [ранее](1-clojure/6-practice.md).

Для наглядности посмотрите тесты. Фейковые реализации устанавиваются с помощью
`(t/use-fixtures :each fakes/fixture)`.

```clojure
(ns publicator.use-cases.interactors.user.register-test
  (:require
   [publicator.use-cases.interactors.user.register :as sut]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.use-cases.abstractions.user-queries :as user-q]
   [publicator.use-cases.test.fakes :as fakes]
   [publicator.utils.test.instrument :as instrument]
   [publicator.use-cases.test.factories :as factories]
   [clojure.test :as t]))

(t/use-fixtures :each fakes/fixture)
(t/use-fixtures :once instrument/fixture)

(t/deftest process
  (let [params     (factories/gen ::sut/params)
        [tag user] (sut/process params)]
    (t/testing "success"
      (t/is (= ::sut/processed tag)))
    (t/testing "logged in"
      (t/is (user-session/logged-in?)))
    (t/testing "persisted"
      (t/is (some? (storage/tx-get-one (:id user)))))))

(t/deftest already-registered
  (let [params (factories/gen ::sut/params)
        _      (factories/create-user {:login (:login params)})
        [tag]  (sut/process params)]
    (t/testing "has error"
      (t/is (= ::sut/already-registered tag)))
    (t/testing "not sign in"
      (t/is (user-session/logged-out?)))))

(t/deftest already-logged-in
  (let [user   (factories/create-user)
        _      (user-session/log-in! user)
        params (factories/gen ::sut/params)
        [tag]  (sut/process params)]
    (t/testing "has error"
      (t/is (= ::sut/already-logged-in tag)))))

(t/deftest invalid-params
  (let [params  {}
        [tag _] (sut/process params)]
    (t/testing "error"
      (t/is (= ::sut/invalid-params tag)))))
```

Желательно покрыть тестами все случаи. Эти случаи указаны в спецификации функций:

```clojure
(s/fdef initial-params
  :args nil?
  :ret (s/or :ok  ::initial-params
             :err ::already-logged-in))

(s/fdef process
  :args (s/cat :params any?)
  :ret (s/or :ok  ::processed
             :err ::already-logged-in
             :err ::invalid-params
             :err ::already-registered))

(s/fdef authorize
  :args nil?
  :ret (s/or :ok  ::authorized
             :err ::already-logged-in))
```

В проекте отсутствуют тесты для `inital-params` и `process`,
вы можете, в качестве парактики, добавить их.
