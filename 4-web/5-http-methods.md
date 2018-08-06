# HTTP методы и HTML

HTTP поддерживает множество методов:
+ get используется только для получения данных.
  Запрос с этим методом может быть закэкширован.
+ post - для создания новой сущности
+ patch - для частичного обновления сущности
+ put - для создания или полной замены сущности, upsert
+ delete - для удаления
+ и т.д.

Однако HTML умеет работать только с get и post.
Get используется при переходе по ссылкам, а post для отправки форм.

Допустим, на странице есть счетчик нажатия кнопки.
Недопустимо использовать get запрос, т.к. он может быть закэширован
браузером, прокси-сервером и т.п. Решить это можно с помощью стилизованной
кнопки отправки формы, ведь post запрос никогда не кэшируется.
Но что, делать, если нужно отпавить delete запрос?

На помощь приходит js. В экосистеме Ruby on Rails есть проект
[rails-ujs](https://github.com/rails/rails/blob/master/actionview/app/assets/javascripts/README.md).
И он доступен отдельно от rails в виде [npm пакета](https://www.npmjs.com/package/rails-ujs).
Благодаря unpkg.com его очень просто добавить на страницу:

```html
<!-- content -->
    <script src="https://unpkg.com/rails-ujs@5.2.0/lib/assets/compiled/rails-ujs.js"></script>
  </body>
</html>
```

Через data атрибут можно указать метод запроса для ссылки:

```html
<a class="nav-item nav-link" data-method="post" href="/some-url">
  some text
</a>
```
