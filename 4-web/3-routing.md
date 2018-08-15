# Routing

Роутинг сопосталяет определенный запрос с обработчиком этого запроса.

Для Ring есть множество библиотек.
Из популярных можно отметить:

+ [compojure](https://github.com/weavejester/compojure)
+ [liberator](https://github.com/clojure-liberator/liberator)
+ [yada](https://github.com/juxt/yada)
+ [bidi](https://github.com/juxt/bidi)

Какую выберете вы, не очень принципиально. Однако, удобно, когда библиотека
поддерживает обратный роутинг, т.е. зная имя роута можно получить url запроса.
Иначе придется хардкодить url строками, и легко оставить битую ссылку.

Воспользуемся библиотекой [sibiro](https://github.com/aroemers/sibiro).
Это простая библиотека, не имеющая лишних для нашего приложения функций.

```clojure
(def routes
  #{[:get "/" #'root :root]
    [:get "/:page" #'page :page]})
```

Каждый маршрут моделируется вектором со следующими элементами:

1. http метод
2. шаблон url, с поддержкой параметров и регулярных выражений
3. функция-обработчик
4. название маршрута для обратоного роутинга

Маршруты объявляются как множество, т.е. маршруты не имеют порядка.
Нет вложенности, невозможно задать middeware для группы роутов,
например для `/admin`. Подробнее ознакомьтесь с [readme](https://github.com/aroemers/sibiro) библиотеки.

Напомню, что ранее мы обсуждали перезагрузку кода.
Если бы мы не использовали `#'root`, а просто передавали значение `root`,
то при изменении кода пришлось бы перезагружать все приложение целиком.

Для примера напишем сайт из нескольких страниц.
Главная страница содержит заголовок и список ссылок на прочие странцы.
Страницы содержат заголовок и ссылаются на главную.

```clojure
(ns app.app
  (:require
   [clojure.string :as str]
   [ring.util.response :as ring.response]
   [sibiro.core]
   [sibiro.extras]))

(declare routes)

(defn path-for [& args]
  (let [ret (apply sibiro.core/path-for routes args)]
    (assert (some? ret) (str "route not found for " args))
    ret))

(defn page-link [slug]
  (let [url (path-for :page {:page slug})]
    (str "<div><a href=\"" url "\">" slug "</a></div>")))

(defn root-link []
  (let [url (path-for :root)]
    (str "<div><a href=\"" url "\">root</a></div>")))

(defn root [req]
  (let [body (str "<h1>Root page</h1>"
                  (->> ["about" "contacts" "resources"]
                       (map page-link)
                       str/join))]
    (-> (ring.response/response body)
        (ring.response/header "Content-Type" "text/html"))))

(defn page [req]
  (let [slug (-> req :route-params :page)
        body (str
              "<h1>" slug "</h1>"
              (root-link))]
    (-> (ring.response/response body)
        (ring.response/header "Content-Type" "text/html"))))

(def routes
  (sibiro.core/compile-routes
   #{[:get "/" #'root :root]
     [:get "/:page" #'page :page]}))

(def handler (sibiro.extras/make-handler routes))
```

Библиотечня функция `sibiro.core/path-for` возвращает `nil`, если не находит подходящий маршрут,
что приводит к коварным ошибкам. Будем использовать обертку `path-for`,
которая бросит исключение, если маршрут не найден.

Запустите этот пример, добавьте парочку маршрутов.
[Исходники примера](/sources/4-web/3-routing).
