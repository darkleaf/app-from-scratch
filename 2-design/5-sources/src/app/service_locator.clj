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

(comment
 (main))
