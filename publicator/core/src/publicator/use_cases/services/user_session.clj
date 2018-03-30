(ns publicator.use-cases.services.user-session
  (:require
   [publicator.use-cases.abstractions.session :as session]
   [publicator.use-cases.abstractions.storage :as storage]))

(defn user-id []
  (session/get ::id))

(defn logged-in? []
  (boolean (user-id)))

(defn logged-out? []
  (not (logged-in?)))

(defn log-in! [user]
  (session/set! ::id (:id user)))

(defn log-out! []
  (session/set! ::id nil))

(defn user []
  (when-let [id (user-id)]
    (storage/tx-get-one id)))

(defn iuser [t]
  (when-let [id (user-id)]
    (storage/get-one t id)))
