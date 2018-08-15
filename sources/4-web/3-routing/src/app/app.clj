(ns app.app
  (:require
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [ring.adapter.jetty :as jetty]
   [ring.util.response :as ring.response]
   [sibiro.core]
   [sibiro.extras]))

(declare routes)

(defn path-for [& args]
  (let [ret (apply sibiro.core/path-for routes args)]
    (assert (some? ret) (str "route not found for " args))
    ret))

(defn page-link [slug]
  (let [url (path-for :page {:page slug})]
    (str "<div><a href=\"" url "\">" slug "</a></div>")))

(defn root-link []
  (let [url (path-for :root)]
    (str "<div><a href=\"" url "\">root</a></div>")))

(defn root [req]
  (let [body (str "<h1>Root page</h1>"
                  (->> ["about" "contacts" "resources"]
                       (map page-link)
                       str/join))]
    (-> (ring.response/response body)
        (ring.response/header "Content-Type" "text/html"))))

(defn page [req]
  (let [slug (-> req :route-params :page)
        body (str
              "<h1>" slug "</h1>"
              (root-link))]
    (-> (ring.response/response body)
        (ring.response/header "Content-Type" "text/html"))))

(def routes
  (sibiro.core/compile-routes
   #{[:get "/" #'root :root]
     [:get "/:page" #'page :page]}))

(def handler (sibiro.extras/make-handler routes))

(defrecord Jetty [val]
  component/Lifecycle
  (start [this]
    (if val
      this
      (assoc this :val
             (jetty/run-jetty handler
                              {:port 4445
                               :join? false}))))
  (stop [this]
    (if-not val
      this
      (do
        (.stop val)
        (assoc this :val nil)))))

(defn build-jetty []
  (->Jetty nil))
