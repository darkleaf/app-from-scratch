(ns publicator.web.components.jetty-test
  (:require
   [publicator.web.components.jetty :as sut]
   [com.stuartsierra.component :as component]
   [clj-http.client :as client]
   [clojure.test :as t]))

(def port 9999)

(t/use-fixtures :each
  (fn [t]
    (let [system (component/system-map
                  :binding-map {:val {}}
                  :jetty (component/using (sut/build {:port port})
                                          [:binding-map]))
          system (component/start system)]
      (try
        (t)
        (finally
          (component/stop system))))))

(t/deftest ok
  (let [resp (client/get (str "http://localhost:" port))]
    (t/is (= 200 (:status resp)))))
