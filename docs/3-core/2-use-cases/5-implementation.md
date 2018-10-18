# Реализация интеракторов

Рассмотрим 2 интерактора. Остальные рассмотрите самостоятельно.

## Отображение поста

Помимо аттрибутов поста ответ должен содержать идентификатор и имя автора.
[Ранее](/3-core/2-use-cases/4-queries) мы рассматривали устройство `abstractions.post-queries`.

Интерактор содержит только метод `process`, т.к. нам не нужна форма и все пользователи
системы могут смотреть все посты. Результатом может быть или успех или неудача из-за того,
что поста нет в хранилище.

```clojure
(ns publicator.use-cases.interactors.post.show
  (:require
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.use-cases.abstractions.post-queries :as post-q]
   [publicator.domain.aggregates.post :as post]
   [darkleaf.either :as e]
   [clojure.spec.alpha :as s]))

(defn- get-by-id= [id]
  (if-let [post (post-q/get-by-id id)]
    (e/right post)
    (e/left [::not-found])))

(defn process [id]
  (e/extract
   (e/let= [user (user-session/user)
            post (get-by-id= id)]
     [::processed post])))

(s/def ::not-found (s/tuple #{::not-found}))
(s/def ::processed (s/tuple #{::processed} ::post-q/post))

(s/fdef process
  :args (s/cat :id ::post/id)
  :ret (s/or :ok  ::processed
             :err ::not-found))
```

Для моделирования вычислений, могут окончиться неудачей воспользуемся монадой either,
которую мы реализовывали [ранее](/1-clojure/6-practice).

Спецификация `process` описывает все возможные ответы.

Тест:

```clojure
(ns publicator.use-cases.interactors.post.show-test
  (:require
   [publicator.use-cases.interactors.post.show :as sut]
   [publicator.use-cases.test.fakes :as fakes]
   [publicator.utils.test.instrument :as instrument]
   [publicator.use-cases.test.factories :as factories]
   [clojure.test :as t]))

(t/use-fixtures :each fakes/fixture)
(t/use-fixtures :once instrument/fixture)

(t/deftest process
  (let [post       (factories/create-post)
        post-id    (:id post)
        user       (factories/create-user {:posts-ids #{post-id}})
        [tag post] (sut/process (:id post))]
    (t/is (= ::sut/processed tag))
    (t/is (some? post))))
```

Желательно, чтобы тесты покрывали все возможные ответы. Вы даже можете на основе спецификаций
автоматически проверять наличие тестов для каждого типа ответа, но не будем отвлекаться.

## Редактирование поста

Прежде всего мы должны проверить, что пользователь залогинен и является автором этого поста.
Затем мы проверяем новые атрибуты поста, при этом мы не должны записать лишние поля,
которые может передать злоумышленник.
Далее мы устанавливаем измененные атрибуты.

```clojure
(ns publicator.use-cases.interactors.post.update
  (:require
   [publicator.domain.aggregates.post :as post]
   [publicator.domain.identity :as identity]
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.utils.spec :as utils.spec]
   [darkleaf.either :as e]
   [clojure.spec.alpha :as s]))

(s/def ::params (utils.spec/only-keys :req-un [::post/title ::post/content]))

(defn- check-authorization= [t id]
  (let [iuser (user-session/iuser t)]
    (cond
      (nil? iuser)                             (e/left [::logged-out])
      (not (contains? (:posts-ids @iuser) id)) (e/left [::not-authorized])
      :else                                    (e/right [::authorized]))))

(defn- find-post= [t id]
  (if-some [ipost (storage/get-one t id)]
    (e/right ipost)
    (e/left [::not-found])))

(defn- check-params= [params]
  (if-some [ed (s/explain-data ::params params)]
    (e/left [::invalid-params ed])))

(defn- update-post [ipost params]
  (dosync (alter ipost merge params)))

(defn- post->params [post]
  (select-keys post [:title :content]))

(defn initial-params [id]
  (storage/with-tx t
    (e/extract
     (e/let= [ok     (check-authorization= t id)
              ipost  (find-post= t id)
              params (post->params @ipost)]
       [::initial-params @ipost params]))))

(defn process [id params]
  (storage/with-tx t
    (e/extract
     (e/let= [ok    (check-authorization= t id)
              ok    (check-params= params)
              ipost (find-post= t id)]
       (update-post ipost params)
       [::processed @ipost]))))

(defn authorize [ids]
  (storage/with-tx t
    (->> ids
         (map #(check-authorization= t %))
         (map e/extract))))

(s/def ::logged-out (s/tuple #{::logged-out}))
(s/def ::invalid-params (s/tuple #{::invalid-params} map?))
(s/def ::not-found (s/tuple #{::not-found}))
(s/def ::not-authorized (s/tuple #{::not-authorized}))
(s/def ::initial-params (s/tuple #{::initial-params} ::post/post map?))
(s/def ::processed (s/tuple #{::processed} ::post/post))
(s/def ::authorized (s/tuple #{::authorized}))

(s/fdef initial-params
  :args (s/cat :id ::post/id)
  :ret (s/or :ok  ::initial-params
             :err ::logged-out
             :err ::not-authorized
             :err ::not-found))

(s/fdef process
  :args (s/cat :id ::post/id
               :params any?)
  :ret (s/or :ok  ::processed
             :err ::logged-out
             :err ::not-authorized
             :err ::not-found
             :err ::invalid-params))

(s/fdef authorize
  :args (s/cat :ids (s/coll-of ::post/id))
  :ret (s/coll-of (s/or :ok  ::authorized
                        :err ::logged-out
                        :err ::not-found
                        :err ::not-authorized)))

```

`clojure.spec` из коробки не поддерживает строгую валидацию ключей, поэтому воспользуемся
собственным макросом `utils.spec/only-keys`.

Пост - множественный ресурс, в отличие от, например, регистрации.
Скажем, при отображении списка постов нужно показать пользователю,
какие посты он может редактировать, а какие нет.
По этой причине `authorize` должен принимать множество идентификаторов,
чтобы избежать проблемы N+1.

В нашем случае `check-authorization=` оперирует только идентификатором поста и не нужно
выбирать из хранилища все посты для переданных `ids` в `authorize`.
Но если бы нам нужно было быть в `check-authorization=` использовать сам пост, то можно
воспользоваться identity-map:

```clojure
(defn- check-authorization= [t id]
  (let [iuser (user-session/iuser t)
        ipost (storage/get-one t id)] ;; <1>
    (some-logic iuser ipost)))

(defn authorize [ids]
  (storage/with-tx t
    (storage/preload ids) ;; <2>
    (->> ids
         (map #(check-authorization= t %))
         (map e/extract))))
```

Как видно, `check-authorization=` принимает объект транзакции `t`, который хранит
кэш выбранных сущностей в рамках этой транзакции. Поэтому в `<1>` будет выборка из кэша,
т.к. в `<2>` мы предварительно загрузили все сущности одним запросом.

## Задание

Самостоятельно посмотрите оставшиеся интеракторы и их тесты.
Тесты покрывают не все случаи, допишите их.
