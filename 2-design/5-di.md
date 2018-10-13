# Dependency injection

Есть принцип Dependency Inversion (DIP),
но он не говорит как именно объект получает свои зависимости.

Есть несколько способов внедрить в объект зависимость:

+ через конструктор / сеттер
+ с помощью Service Locator

Рассмотрим их на примере http приложения, создающего пользователей.
Оно состоит из:

+ роутера
+ экшена контроллера
+ юзкейса
+ сущности пользователя

При создании, пользователь должен получить идентификатор.
Также приложение должно выслать уведомление.

Именно для IdGenerator и Notifier будет применяться принцип инверсии зависимости,
т.е. приложение будет знать только об интерфейсе, а не реализации зависимостей.

## Внедрение через конструктор

Сначала разберем пример на js, а потом перейдем к clojure.

Сущность User - простая структура, просто принимающая id и логин через конструктор:

```javascript
class User {
  constructor(id, login) {
    this.id = id;
    this.login = login;
  }
}
```

Чтобы при создании пользователя ему устанавливался сгенерированный идентификатор, нужно
создавать пользователя с помощью фабрики:

```javascript
class User {
  constructor(id, login) {
    this.id = id;
    this.login = login;
  }
}

class UserFactory {
  constructor(idGenerator) {
    this.idGenerator = idGenerator;
  }

  build(login) {
    const id = this.idGenerator.generate();
    return User.new(id, login);
  }
}
```

Как раз в фабрику через конструктор внедряется idGenerator.
И фабрика знает только о его интерфейсе, т.е. о методе `generate`, но не о его реализации.

В clojure нет привычных конструкторов. Воспользуемся функциями.
Названия функций-конструкторов будут начинаться с `->`, чтобы отличить их от просто функций.

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
При этом нужно явно связывать компоненты в функции main.

При этом в одном рантайме мы можем иметь сколько угодно копий приложения с разными зависимостями,
т.к. мы не используем глобальное состояние.

В примитивном случае такой подход требует явного конфигурирования зависимостей,
как это сделано в функции `main`.

Представьте себе приложение из пары десятков юзкейсов.
Сколько зависимостей придется явно сконфигурировать?

Существуют целые фреймворки(IoC container) для автоматического управления зависимостями.
Некоторые из них вместо явного конфигурирования
[самостоятельно ищут](https://autofac.readthedocs.io/en/latest/register/scanning.html)
реализации абстракций (Convention over Configuration).

## Service locator

Локатор сервисов - глобальный объект, разрешающий зависимость.
В мире статически типизированных языков считается
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

В этом случае между компонентами есть явные зависимости на уровне исходного кода,
и внедряются только `id-generator` и `notifier`.

В случае clojure локатор сервисов напоминает Var. Только Var хранит одну зависимость
и менее нагляден. Мы всегда можем заменить корневое значение переменной с помощью
[`with-redefs`](https://clojuredocs.org/clojure.core/with-redefs).

При использовании service locator и with-redefs в одном рантайме может быть только
одна копия приложения, т.к. используется глобальное стояние.

## Dynamic binding

Мы можем использовать динамические переменные, которые позволяют устанавливать
их значение для текущего потока исполнения. При этом clojure функции умеют сохранять
этот контекст и предавать его во вновь созданные потоки из текущего потока.

```clojure
(ns app.binding)

(declare ^:dynamic *id-generator*)
(declare ^:dynamic *notifier*)

(defrecord User [id login])

(defn user-factory [login]
  (->User (*id-generator*) login))

(defn create-user-use-case [login]
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
  (binding [*id-generator* (->id-generator)
            *notifier*     (->notifier)]
    (routing {:url "/users", :params {:login "Admin"}})))

(main)
```

Таким образом мы сократили количество зависимостей и получили возможность
запускать несколько копий приложения в одном рантайме.

## Sources

[Исходники](/sources/2-design/5-di)

## Ссылки

+ https://www.manning.com/books/dependency-injection-in-dot-net
+ https://www.martinfowler.com/articles/injection.html
