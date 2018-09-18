# Презентер

Презентер возвращает view model, которая пердается в шаблон.
View model содержит все данные, всю логику, так, чтобы шаблон был максимально простым и
не требовал модульного тестирования. Все ссылки, активность кнопок устанавливаются тут.
Таким образом при необходимости легко написать модульный тест для презентера.

```clojure
(ns publicator.web.presenters.layout
  (:require
   [publicator.use-cases.services.user-session :as user-session]
   [publicator.web.routing :as routing]
   [ring.middleware.anti-forgery :as anti-forgery]))

(defn present [req]
  (cond-> {:csrf anti-forgery/*anti-forgery-token*}
    (user-session/logged-in?)
    (assoc :log-out {:text   "Log out"
                     :url    (routing/path-for :user.log-out/process)})

    (user-session/logged-out?)
    (assoc :register {:text "Register"
                      :url  (routing/path-for :user.register/initial-params)})

    (user-session/logged-out?)
    (assoc :log-in {:text "Log in"
                    :url  (routing/path-for :user.log-in/initial-params)})))
```

```clojure
(ns publicator.web.presenters.post.list
  (:require
   [publicator.use-cases.interactors.post.list :as interactor]
   [publicator.use-cases.interactors.post.create :as interactors.post.create]
   [publicator.use-cases.interactors.post.update :as interactors.post.update]
   [publicator.domain.aggregates.user :as user]
   [publicator.web.routing :as routing]))

(defn- post->model [post authorization]
  {:id             (:id post)
   :url            (routing/path-for :post.show/process {:id (-> post :id str)})
   :update-url     (routing/path-for :post.update/initial-params {:id (-> post :id str)})
   :title          (:title post)
   :can-update?    (= [::interactors.post.update/authorized] authorization)
   :user-full-name (::user/full-name post)})

(defn processed [posts]
  (let [authorizations (interactors.post.update/authorize (map :id posts))
        view-models    (map post->model posts authorizations)
        can-create?    (= [::interactors.post.create/authorized]
                          (interactors.post.create/authorize))]
    (cond-> {}
      :always     (assoc :posts view-models)
      can-create? (assoc :new {:text "New"
                               :url  (routing/path-for :post.create/initial-params)}))))
```
