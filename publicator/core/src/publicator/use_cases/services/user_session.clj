(ns publicator.use-cases.services.user-session
  (:require
   [publicator.use-cases.abstractions.session :as session]))

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
