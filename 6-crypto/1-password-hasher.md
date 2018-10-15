# Password hasher

Для шифрования паролей и их проверки воспользуемся библиотекой
[buddy-hashers](https://funcool.github.io/buddy-hashers/latest/).

Напомню абстракцию:

```clojure
(ns publicator.domain.abstractions.password-hasher
  (:refer-clojure :exclude [derive])
  (:require [clojure.spec.alpha :as s]))

;; check нужет, т.к. derive для одного и того же пароля может давать разные результаты,
;; т.к. результат может содержать случайную соль

(defprotocol PasswordHasher
  (-derive [this password])
  (-check [this attempt encrypted]))

(declare ^:dynamic *password-hasher*)

(s/def ::password string?)
(s/def ::encrypted string?)

(s/fdef derive
  :args (s/cat :password ::password)
  :ret ::encrypted
  :fn #(not= (-> % :args :password)
             (-> % :ret)))

(defn derive [password]
  (-derive *password-hasher* password))


(s/fdef check
  :args (s/cat :attempt ::password
               :encrypted ::encrypted)
  :ret boolean?)

(defn check [attempt encrypted]
  (-check *password-hasher* attempt encrypted))
```

Вот ее реализация:

```clojure
(ns publicator.crypto.password-hasher
  (:require
   [buddy.hashers]
   [publicator.domain.abstractions.password-hasher :as password-hasher]))

(deftype PasswordHasher []
  password-hasher/PasswordHasher
  (-derive [_ password]
    (buddy.hashers/derive password))
  (-check [_ attempt encrypted]
    (buddy.hashers/check attempt encrypted)))

(defn binding-map []
  {#'password-hasher/*password-hasher* (PasswordHasher.)})
```

И тест:

```clojure
(ns publicator.crypto.password-hasher-test
  (:require
   [clojure.test :as t]
   [publicator.utils.test.instrument :as instrument]
   [publicator.crypto.password-hasher :as sut]
   [publicator.domain.abstractions.password-hasher :as password-hasher]))

(defn- setup [t]
  (with-bindings (sut/binding-map)
    (t)))

(t/use-fixtures :once
  instrument/fixture)

(t/use-fixtures :each
  setup)

(t/deftest ok
  (let [pass   "strong password"
        digest (password-hasher/derive pass)]
    (t/is (password-hasher/check pass digest))))
```
