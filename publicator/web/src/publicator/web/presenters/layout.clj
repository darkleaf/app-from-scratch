(ns publicator.web.presenters.layout
  (:require
   [sibiro.core :as sibiro]))

(defn present [{:keys [routes]}]
  {
   ;; :log-out {:visible false
   ;;           :text "Log out"
   ;;           :url "/aaaaaa"
   ;;           :method "post"}
   ;; :register {:visible false
   ;;            :text "Register"
   ;;            :url "/aaaa"}
   :log-in {:visible true
            :text "Log in"
            :url (sibiro/path-for routes :user.log-in/form)}})
