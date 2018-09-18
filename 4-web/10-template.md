# Шаблон

Можно использовать различные шаблонизаторы, в том числе и для java:

+ [hiccup](https://github.com/weavejester/hiccup)
+ [selmer](https://github.com/yogthos/Selmer)
+ [cljstache](https://github.com/fotoetienne/cljstache)
+ [jade4j](https://github.com/neuland/jade4j) (java)
+ [thymeleaf](https://www.thymeleaf.org) (java)

Java шаблонизаторы принимают `Map<String, Object>` в качестве модели.
Generics - это особенность Java, но не JVM, а clojure map
[поддерживают интерфейс Map](https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/APersistentMap.java#L16), поэтому мы можем передавать просто хэши с строковыми ключами.

Из всех перечисленных шаблонизаторов самым простым является cljstache, с ним и будем работать.

Для рендеринга воспользуемся простой оберткой:

```clojure
(ns publicator.web.template
  (:require
   [cljstache.core :as mustache]))

(defn render [template-name data]
  (let [path (str "publicator/web/templates/" template-name ".mustache")]
    (mustache/render-resource path data)))
```

Вот примеры шаблонов, для презентеров, рассмотренных ранее:

```mustache
<!DOCTYPE html>
<html>
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">

        <meta name="csrf-token" content="{{csrf}}">
        <meta name="csrf-param" content="__anti-forgery-token">

        <link rel="stylesheet"
              href="https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css"
              integrity="sha384-Gn5384xqQ1aoWXA+058RXPxPg6fy4IWvTNh0E263XmFcJlSAwiGgFAW/dAiS6JXm"
              crossorigin="anonymous">
    </head>
    <body>
        <nav class="navbar navbar-expand-lg navbar-light bg-light">
            <div class="container">
                <a class="navbar-brand" href="/">Publicator</a>
                <div class="navbar-nav mr-auto">
                    <a class="nav-item nav-link" href="/posts">Posts</a>
                </div>
                <div class="navbar-nav">
                    {{#log-in}}
                        <a class="nav-item nav-link"
                           href="{{url}}">
                            {{text}}
                        </a>
                    {{/log-in}}

                    {{#register}}
                        <a class="nav-item nav-link"
                           href="{{url}}">
                            {{text}}
                        </a>
                    {{/register}}

                    {{#log-out}}
                        <a class="nav-item nav-link"
                           data-method="post"
                           href="{{url}}">
                            {{text}}
                        </a>
                    {{/log-out}}
                </div>
            </div>
        </nav>
        <div class="container">
            {{&content}}
        </div>

        <script src="https://unpkg.com/rails-ujs@5.2.0/lib/assets/compiled/rails-ujs.js"></script>
        <script src="https://unpkg.com/form-ujs@0.0.2/dist/form-ujs.js"></script>
    </body>
</html>
```

```mustache
{{#new}}
    <a href="{{url}}" class="btn btn-primary my-3">
        {{text}}
    </a>
{{/new}}

<table class="table">
    <thead>
        <tr>
            <th scope="col">#</th>
            <th scope="col">Title</th>
            <th scope="col">Author</th>
            <th scope="col">Actions</th>
        </tr>
    </thead>
    <tbody>
        {{#posts}}
            <tr>
                <th scope="row">{{id}}</th>
                <td>
                    <a href="{{url}}">{{title}}</a>
                </td>
                <td>{{user-full-name}}</td>
                <td>
                    {{#can-update?}}
                        <a href="{{update-url}}">edit</a>
                    {{/can-update?}}
                </td>
            </tr>
        {{/posts}}
    </tbody>
</table>
```
