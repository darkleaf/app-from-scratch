# Формы

Есть несколько способов работать с формами в web.

1. HTML формы, формируемые на backend. Подходит для простых случаев.
   Никаких автокомплитов, date-picker, вложенных форм и т.п.
2. HTML формы, формируемые на backend + js.
   Значительно лучше.
   Но в проект добавляется новая компонента - frontend.
   Приходится работать с другой технологией, управлять npm пакетами, использовать системы сборки.
   Появляются проблемы сериализации данных, например json не умеет сериализовывать даты.
   Сложно тестировать, т.к. это только интеграционные тесты с selenium и т.п.
3. Формы на js. Больше возможностей, сложнее формы. Но логика еще сильнее расползается
   между backend и frontend. Для специализированных или сложных форм это единственное решение.

Выделю следующие проблемы:

1. Кроме backend появляется еще и frontend.
   Разработчик должен овладеть новыми инструментами
   или команда пополняется frontend разработчиком.
2. Возникают проблемы с передачей данных.
   Скажем, некоторые поля формы имеют тип Date, UUID или множество Keyword.
   Приходится явно прописывать правила сериализации/десериализации.
3. Расползается логика, скажем в html добавили поле, а js - забыли.
4. Сложно тестировать.

Для типовых форм, которые, например, используются в админках можно решить эти проблемы.

## Transit format

Для безболезненной передачи данных с бэкенда на фронтенд и обратно воспользуется форматом
[transit](https://github.com/cognitect/transit-format).
[transit-clj](https://github.com/cognitect/transit-clj) - библиотека для бэкенда,
поддерживает все стандартные clojure типы и позволяет добавить собственные.
[transit-js](https://github.com/cognitect/transit-js) - библиотека для фронтенда,
добавляет свои типы для работы с transit типами.

В качестве транспорта используется json, а браузер имеет встроенную оптимизированную поддержку json,
поэтому сериализация/десериализация происходит очень быстро.
Транзит поддерживает замену повторяющихся частей короткими идентификаторами,
поэтому, например массивы хешей занимают меньше места, чем json:

```clojure
(def some-ids [{:very-long-id 1} {:very-long-id 2} {:very-long-id 3}])
(t/write w some-ids)
```

Результат:
```json
[["^ ","~:very-long-id",1],["^ ","^0",2],["^ ","^0",3]]
```

Т.е. последующие упоминания `:very-long-id` заменяются на `^0`.

Подробнее вы можете прочитать в статьях:

+ [Transit format: An interactive tutorial - better than JSON (part 1)](https://blog.klipse.tech/clojure/2016/09/22/transit-clojure.html)
+ [https://blog.klipse.tech/clojure/2016/09/22/transit-clojure-2.html](https://blog.klipse.tech/clojure/2016/09/22/transit-clojure-2.html)

Transit-js добавляет свои типы:

```javascript
import t from 'transit-js';
const kw = t.keyword;

t.map([
  kw('widget'), kw('input'),
  kw('type'), 'password',
  kw('label'), 'Password',
]),
```

в clojure это эквивалентно:

```clojure
{:widget :input
 :type   "password"
 :label  "Password"}
```

## Form-ujs

В мире Ruby on Rails популярен подход "Ненавязчивый javascript (Unobtrusive javascript)".
Ненавязчивость подразумевает, что js на странице есть, но мы его не пишем.
Ранее мы [знакомились](/4-web/4-http-methods.md) с проектом rails-ujs, который следует этой парадигме.

По аналогии я написал прототип библиотеки [form-ujs](https://github.com/darkleaf/form-ujs),
которая находит на странице описание формы и рендерит ее.

В код страницы нужно добавить один js тэг:

```html
<script src="https://unpkg.com/form-ujs@0.0.2/dist/form-ujs.js"></script>
```

Бэкенд описывает форму в терминах стандартных виджетов:

```clojure
(ns publicator.web.forms.user.register
  (:require
   [publicator.web.routing :as routing]))

(defn description []
  {:widget :submit, :name "Зарегистрироваться"
   :url (routing/path-for :user.register/process), :method :post, :nested
   {:widget :group, :nested
    [:login {:widget :input, :label "Логин"}
     :full-name {:widget :input, :label "Полное имя"}
     :password {:widget :input, :label "Пароль", :type "password"}]}})

(defn build [initial-params]
  {:initial-data initial-params
   :errors       {}
   :description  (description)})
```

Которое добавляется на страницу:

```html
<div data-form-ujs='["^ ","~:initial-data",["^ "],"~:errors",["^ "],"~:description",["^ ","~:widget","~:submit","~:name","Зарегистрироваться","~:url","/register","~:method","~:post","~:nested",["^ ","^3","~:group","^9",["~:login",["^ ","^3","~:input","~:label","Логин"],"~:full-name",["^ ","^3","^<","^=","Полное имя"],"~:password",["^ ","^3","^<","^=","Пароль","~:type","password"]]]]]' />
```

Результат можно посмотреть на [демо-сайте](https://darkleaf-publicator2.herokuapp.com/register).

## Ошибки

Виджет `submit` по клику на кнопку отправляет данные на сервер.
В случае успеха сервер может прислать редирект, а в случае ошибок - структуру с ошибками.

Для валидации используется `clojure.spec` и нужно привести эту структуру к человекопонятному виду:

```clojure
(ns publicator.web.presenters.explain-data
  (:require
   [clojure.spec.alpha :as s]
   [phrase.alpha :as phrase]))

;; todo: использовать локализацию, например: https://github.com/tonsky/tongue

(phrase/defphraser :default
  [ctx {:keys [in]}]
  [in "Неизвестная ошибка"])

(phrase/defphraser #(contains? % k)
  [ctx {:keys [in]} k]
  [(conj in k) "Обязательное"])

(phrase/defphraser string?
  [ctx {:keys [in]}]
  [in "Должно быть строкой"])

(phrase/defphraser #(re-matches re %)
  [ctx {:keys [in]} re]
  (or
   (when-some [[_ r-min r-max] (re-matches #"\\w\{(\d+),(\d+)\}" (str re))]
     [in (str "Кол-во латинских букв и цифр от " r-min " до " r-max)])
   (when-some [[_ r-min r-max] (re-matches #"\.\{(\d+),(\d+)\}" (str re))]
     [in (str "Кол-во символов от " r-min " до " r-max)])
   [in "Неизвестная ошибка"]))

(defn ->errors [explain-data]
  (let [problems (::s/problems explain-data)
        pairs    (map #(phrase/phrase :ctx %) problems)]
    (reduce
     (fn [acc [in message]]
       (assoc-in acc (conj in :form-ujs/error) message))
     {}
     pairs)))
```

```clojure
(ns publicator.web.presenters.explain-data-test
  (:require
   [clojure.test :as t]
   [clojure.spec.alpha :as s]
   [publicator.web.presenters.explain-data :as sut]))

(s/def ::for-required (s/keys :req-un [::required-1 ::required-2]))

(t/deftest required
  (let [ed     (s/explain-data ::for-required {})
        errors (sut/->errors ed)]
    (t/is (= {:required-1    {:form-ujs/error "Обязательное"}
              :required-2 {:form-ujs/error "Обязательное"}}
             errors))))

(s/def ::login (s/and string? #(re-matches #"\w{3,255}" %)))
(s/def ::password (s/and string? #(re-matches #".{8,255}" %)))

(s/def ::for-regexp-w (s/keys :req-un [::login]))
(s/def ::for-regexp-. (s/keys :req-un [::password]))

(t/deftest regexp
  (t/testing "\\w"
    (let [ed     (s/explain-data ::for-regexp-w {:login ""})
          errors (sut/->errors ed)]
      (t/is (= {:login {:form-ujs/error "Кол-во латинских букв и цифр от 3 до 255"}}
               errors))))
  (t/testing "."
    (let [ed     (s/explain-data ::for-regexp-. {:password ""})
          errors (sut/->errors ed)]
      (t/is (= {:password {:form-ujs/error "Кол-во символов от 8 до 255"}}
               errors)))))
```
