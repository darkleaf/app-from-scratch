# Адаптер

Весь подпроект web является адаптером между слоем сценариев и веб-сервером.
Это можно показать так:

```clojure
(defn handler [req]
  (let [args   (req->args req)
        result (apply interactor args)
        resp   (result->resp result)]
     resp))
```

Т.е. обработчик имеет 2 обязанности:
+ преобразование ring запроса в аргументы интерактора, показана как функция `req->args`
+ преобразование результата интерактора к ring ответу, показана как функция `result->resp`

Разделим обработчик на составные части.
Назовем `req->args` контроллером, а `result->resp` респондером.

```clojure
(defn controller [req]
  (let [arg1 (req->arg1 req)
        arg2 (req->arg2 req)]
    [interactor arg1 arg2])) ;; <1>

(defn responder [result [arg1 arg2]]
  (let [viewmodel (presenter result arg1 arg2)
        html      (template viewmodel)]
    {:status  200
     :headers {}
     :body    html}))

(defn middleware [handler]
  (fn [req]
    (let [[interactor & args] (handler req)
          result              (apply interactor args)]
      (responder result))))

(def handler
  (-> controller
      middleware))
```

Теперь у нас 4 слабосвязанных компонента.

Middleware и handler существуют в единственном экземпляре, тривиальны и
не требуют модульного тестирования.

Контроллеры и респондеры не зависят друг от друга.
[Вспомним](/2-design/3-clean-architecture.md)
диаграмму
связи интерактора, контроллера и презентера.
В этом случае респондер - это презентер в терминах диаграммы.

Обратите внимание на <1>, контроллер не вызывает интерактор, а просто
возвращает функцию и ее аргументы как данные. Таким образом
при тестировании не нужно подменять интерактор, чтобы проверить
корректность формирования его аргументов.

Все это делает модульное тестирование очень простым.

В следующих параграфах мы разберем эти компоненты подробнее.
