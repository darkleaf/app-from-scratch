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

(comment
 (main))
