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
        create-user-action   (->create-user-action create-user-use-case)]
    (create-user-action {:params {:login "Admin"}})))

(comment
 (main))
