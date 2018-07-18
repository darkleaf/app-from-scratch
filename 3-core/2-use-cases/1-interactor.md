# Interactor

Интерактор - реализация сценария взаимодействия пользователя с системой.
Их еще называют Use Case. Термин интерактор пришел из книги Clean Architecture.
В нашем приложении слой, содержащий интеракторы и вспомогательные неймспейсы,
будет называться use-cases.

Мы разрабатываем веб-приложение, поэтому взаимодействие пользователя с системой
стоится по модели запрос-ответ. Приложения с интерфейсом командной строки или десктопные приожения
стоются по этой же модели.

Каждый интерактор представляет из себя функцию, принимающую данные и возвращающую данные.

Рассмотрим регистрацию пользователя. Получается 2 запроса к системе:
отображение формы и ее обработка.

Форма будет содержать 2 поля: логин и пароль.

Перед тем, как отобразить форму, мы должны проверить, что пользователь разлогинен.
Если бы мы просили указать страну, то логично было бы задать значение по умолчанию,
определив страну пользователя, по ip адресу.

При обработки формы мы должны выполнить следующие шаги:

+ проверить, что пользователь разлогинен
+ проверить входные параметры
+ проверить, что пользователь еще не зарегистриован
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

(defn- check-logged-out= []
  (if (user-session/logged-out?)
    (e/right)
    (e/left [::already-logged-in])))

(defn- check-params= [params]
  (if-let [exp (s/explain-data ::params params)]
    (e/left [::invalid-params exp])
    (e/right)))

(defn- check-not-registered= [params]
  (if (user-q/get-by-login (:login params))
    (e/left [::already-registered])
    (e/right)))

(defn- create-user [params]
  (storage/tx-create (user/build params)))

(defn ^:dynamic *initial-params* []
  @(e/let= [ok (check-logged-out=)]
     [::initial-params {}]))

(defn ^:dynamic *process* [params]
  @(e/let= [ok   (check-logged-out=)
            ok   (check-params= params)
            ok   (check-not-registered= params)
            user (create-user params)]
     (user-session/log-in! user)
     [::processed user]))

(s/def ::already-logged-in (s/tuple #{::already-logged-in}))
(s/def ::invalid-params (s/tuple #{::invalid-params} map?))
(s/def ::already-registered (s/tuple #{::already-registered}))
(s/def ::initial-params (s/tuple #{::initial-params} map?))
(s/def ::processed (s/tuple #{::processed} ::user/user))

(s/fdef initial-params
  :ret (s/or :ok  ::initial-params
             :err ::already-logged-in))

(s/fdef process
  :args (s/cat :params any?)
  :ret (s/or :ok  ::processed
             :err ::already-logged-in
             :err ::invalid-params
             :err ::already-registered))

(defn initial-params []
  (*initial-params*))

(defn process [params]
  (*process* params))
```

Как видно, код полностью соответствует словестному описанию.

Мы пока не знаем деталей реализации нашего приложения, но уже сейчас нам нужно как-то
получить пользователя по его email, сохранить пользователя и работать с сессией.
Для этого объявляются соответсвующие абстракции.
В предыдущем разделе мы сталкивались с абстракцией генерации идентификаторов и шифрования паролей.
В следующих параграфах мы разберем эти абстракции.

Функции `*process*` и `*inital-params*` обявлены динамическими, что-бы можно было
легко заменить реализацию сценариев заглушками при тестировании web-части приложения.
Функции-обрертки `process` и `initial-params` нужны для объявления спецификаций.
В противоном случае невозможно использовать
[instrument](https://clojure.org/guides/spec#_instrumentation)
для функций, хранящихся в динамических переменных.

Интерактор использует монаду either для реализации вычислений, которые могут окончиться неудачей.
Ранее мы [писали свою версию either](1-clojure/6-practice.md).
Здесь же воспользуемся более удобным [вариантом](https://github.com/darkleaf/either).
Ознакомьтесь с описанием библиотеки, чтобы узанть о различиях.

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
(s/fdef process
  :args (s/cat :params any?)
  :ret (s/or :ok  ::processed
             :err ::already-logged-in
             :err ::invalid-params
             :err ::already-registered))

(s/fdef initial-params
  :ret (s/or :ok  ::initial-params
             :err ::already-logged-in))
```

В проекте отсутствуют тесты для `inital-params`, вы можете, в качестве парактики, дописать их.
