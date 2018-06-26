# Dependency injection

Есть принцип SOLID Dependency Inversion (DIP),
но он не говорит как именно объект получает свои зависимости.

Есть несколько способов внедрить в объект зависимость:

+ через конструктор / сеттер
+ с помощью Service Locator

## Внедрение через конструктор

Функция-конструктор будет начинаться с `->`.

```clojure
(ns app.constructor)

(defrecord User [id login])

(defn ->user-factory [id-generator]
  (fn [login]
    (->User (id-generator) login)))

(defn ->create-user-use-case [user-factory notifier]
  (fn [login]
    (let [user (user-factory login)]
      (notifier user)
      user)))

(defn ->create-user-action [create-user-use-case]
  (fn [req]
    (let [login (-> req :params :login)
          user  (create-user-use-case login)
          id    (:id user)]
      {:status  302
       :headers {"Location" (str "/users/" id)}})))

(defn ->routing [create-user-action]
  (fn [req]
    (cond
      (= (:url req) "/users") (create-user-action req)
      :else {:status 404})))

(defn ->id-generator []
 (let [counter (atom 0)]
   (fn []
     (swap! counter inc))))

(defn ->notifier []
  (fn [user]
    (prn user)))

(defn main []
  (let [id-generator         (->id-generator)
        notifier             (->notifier)
        user-factory         (->user-factory id-generator)
        create-user-use-case (->create-user-use-case user-factory notifier)
        create-user-action   (->create-user-action create-user-use-case)
        routing              (->routing create-user-action)]
    (routing {:url "/users", :params {:login "Admin"}})))

(main)
```

Т.е. каждый компонент не зависит напрямую ни от чего, все зависимости он получает
через конструктор с помощью замыкания.
При этом нужно явно связывать компонеты.

При этом в одном рантайме мы можем иметь сколько угодно копий приложения с разными зависимостями,
т.к. мы не используем глобальное состояние.

Конечно, для модульного тестирования экшена удобно заменить use-case заглушкой,
но удобно или устанавливать зависимости для роутинга? Представьте себе
какой огромной будет функция main для сотни роутов. Хотя нам всего лишь
нужно внедрять `id-generator` и `notifier`, т.к. мы откладываем их реализацию
и в первую очередь релизуем высокоуровневую логику, используя заглушки для деталей.

Существуют целые фреймворки(IoC container) для управления зависимостями.
Например, в .NET мире можно просканировать сборку и, в случае единственной релаизации
зависимости, использовать ее по умолчанию, что решит проблему из предыдущего абзаца.


## Service locator

Локатор сервисов - глобальный объект, разрешающий зависимость.
В мире статически типизированых языков считается
[антипаттерном](http://blog.ploeh.dk/2010/02/03/ServiceLocatorisanAnti-Pattern/).

```clojure
(ns app.service-locator)

(def service-locator (atom {}))

(defrecord User [id login])

(defn user-factory [login]
  (let [{:keys [id-generator]} @service-locator]
    (->User (id-generator) login)))

(defn create-user-use-case [login]
  (let [{:keys [notifier]} @service-locator
        user (user-factory login)]
    (notifier user)
    user))

(defn create-user-action [req]
  (let [login (-> req :params :login)
        user  (create-user-use-case login)
        id    (:id user)]
    {:status  302
     :headers {"Location" (str "/users/" id)}}))

(defn routing [req]
  (cond
    (= (:url req) "/users") (create-user-action req)
    :else {:status 404}))

(defn ->id-generator []
 (let [counter (atom 0)]
   (fn []
     (swap! counter inc))))

(defn ->notifier []
  (fn [user]
    (prn user)))

(defn main []
  (swap! service-locator assoc
         :id-generator (->id-generator)
         :notifier     (->notifier))
  (routing {:url "/users", :params {:login "Admin"}}))

(main)
```

В этом случае между компонентами явные зависимости на уровне исходного кода,
и мы внедряем только `id-generator` и `nofiier`.
При этом для модульного тестирования `create-user-action` мы можем только заменить содержимое
переменной `#'create-user-use-case` с помощью
[`with-redefs`](https://clojuredocs.org/clojure.core/with-redefs).

При использовании service locator и with-redefs в одном рантайме может быть только
одна копия приложения, т.к. мы используем глобальное сотояние.

## Dynamic binding

Мы можем использовать динамические переменные, которые позволяют устанавливать
их значение для текущего потока исполения. При этом clojure функции умеют сохранять
этот контекст и предавать его в вновь созданные потоки из текущего потока.

```clojure
(ns app.binding)

(declare ^:dynamic *id-generator*)
(declare ^:dynamic *notifier*)

(defrecord User [id login])

(defn user-factory [login]
  (->User (*id-generator*) login))

(defn ^:dynamic create-user-use-case [login]
  (let [user (user-factory login)]
    (*notifier* user)
    user))

(defn create-user-action [req]
  (let [login (-> req :params :login)
        user  (create-user-use-case login)
        id    (:id user)]
    {:status  302
     :headers {"Location" (str "/users/" id)}}))

(defn routing [req]
  (cond
    (= (:url req) "/users") (create-user-action req)
    :else {:status 404}))

(defn ->id-generator []
 (let [counter (atom 0)]
   (fn []
     (swap! counter inc))))

(defn ->notifier []
  (fn [user]
    (prn user)))

(defn main []
  (binding [*id-generator*         (->id-generator)
            *notifier*             (->notifier)]
    (routing {:url "/users", :params {:login "Admin"}})))

(main)
```

Отмечу, что переменная `#'create-user-use-case` - помечена как динамическия,
и имеет заданое заначение по умолчанию. Это позволит легко менять ее значение при тестировании,
в том числе в несколько потоков.

## Sources

[Исходники](/3-project/2-sources)

## Ссылки

+ https://www.manning.com/books/dependency-injection-in-dot-net
+ https://www.martinfowler.com/articles/injection.html
