(ns publicator.web.presenters.explain-data-test
  (:require
   [clojure.test :as t]
   [clojure.spec.alpha :as s]
   [publicator.web.presenters.explain-data :as sut]))

(s/def ::for-required (s/keys :req-un [::required-1 ::required-2]))

(t/deftest required
  (let [ed     (s/explain-data ::for-required {})
        errors (sut/->errors ed)]
    (t/is (= {:required-1    {:form-ujs/error "Обязательное"}
              :required-2 {:form-ujs/error "Обязательное"}}
             errors))))

(s/def ::login (s/and string? #(re-matches #"\w{3,255}" %)))
(s/def ::password (s/and string? #(re-matches #".{8,255}" %)))

(s/def ::for-regexp-w (s/keys :req-un [::login]))
(s/def ::for-regexp-. (s/keys :req-un [::password]))

(t/deftest regexp
  (t/testing "\\w"
    (let [ed     (s/explain-data ::for-regexp-w {:login ""})
          errors (sut/->errors ed)]
      (t/is (= {:login {:form-ujs/error "Кол-во латинских букв и цифр от 3 до 255"}}
               errors))))
  (t/testing "."
    (let [ed     (s/explain-data ::for-regexp-. {:password ""})
          errors (sut/->errors ed)]
      (t/is (= {:password {:form-ujs/error "Кол-во символов от 8 до 255"}}
               errors)))))
