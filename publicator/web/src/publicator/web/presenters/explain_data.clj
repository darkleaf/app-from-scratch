(ns publicator.web.presenters.explain-data
  (:require
   [clojure.spec.alpha :as s]
   [phrase.alpha :as phrase]))

;; todo: использовать локализацию, например: https://github.com/tonsky/tongue

(phrase/defphraser :default
  [ctx {:keys [in]}]
  [in "Неизвестная ошибка"])

(phrase/defphraser #(contains? % k)
  [ctx {:keys [in]} k]
  [(conj in k) "Обязательное"])

(phrase/defphraser string?
  [ctx {:keys [in]}]
  [in "Должно быть строкой"])

(phrase/defphraser #(re-matches re %)
  [ctx {:keys [in]} re]
  (or
   (when-some [[_ r-min r-max] (re-matches #"\\w\{(\d+),(\d+)\}" (str re))]
     [in (str "Кол-во латинских букв и цифр от " r-min " до " r-max)])
   (when-some [[_ r-min r-max] (re-matches #"\.\{(\d+),(\d+)\}" (str re))]
     [in (str "Кол-во символов от " r-min " до " r-max)])
   [in "Неизвестная ошибка"]))

(defn ->errors [explain-data]
  (let [problems (::s/problems explain-data)
        pairs    (map #(phrase/phrase :ctx %) problems)]
    (reduce
     (fn [acc [in message]]
       (assoc-in acc (conj in :form-ujs/error) message))
     {}
     pairs)))
