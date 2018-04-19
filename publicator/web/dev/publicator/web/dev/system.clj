(ns publicator.web.dev.system
  (:require
   [com.stuartsierra.component :as component]
   [publicator.web.components.jetty :as jetty]
   [publicator.use-cases.test.factories :as factories]
   [publicator.use-cases.test.fakes.storage :as storage]
   [publicator.use-cases.test.fakes.user-queries :as user-q]
   [publicator.use-cases.test.fakes.post-queries :as post-q]
   [publicator.domain.test.fakes.id-generator :as id-generator]
   [publicator.domain.test.fakes.password-hasher :as password-hasher]))

(defrecord BindingMap [val]
  component/Lifecycle
  (start [this]
    (let [db (storage/build-db)]
      (assoc this :val
             (merge (storage/binding-map db)
                    (user-q/binding-map db)
                    (post-q/binding-map db)
                    (id-generator/binding-map)
                    (password-hasher/binding-map)))))
  (stop [this] this))

(defrecord Seed [binding-map]
  component/Lifecycle
  (start [this]
    (with-bindings (:val binding-map)
      (let [post1 (factories/create-post)
            user1 (factories/create-user {:login "user1"
                                          :password "12345678"
                                          :full-name "User1"
                                          :posts-ids [(:id post1)]})
            post2 (factories/create-post)
            user2 (factories/create-user {:login "user2"
                                          :password "12345678"
                                          :full-name "User2"
                                          :posts-ids [(:id post2)]})]))
    this)
  (stop [this]
    this))

(defn build []
  (component/system-map
   :binding-map (->BindingMap nil)
   :seed (component/using (->Seed nil)
                          [:binding-map])
   :jetty (component/using (jetty/build {:port 4445})
                           [:binding-map])))
