(ns publicator.use-cases.test.fakes.storage
  (:require
   [publicator.domain.identity :as identity]
   [publicator.domain.abstractions.aggregate :as aggregate]
   [publicator.use-cases.abstractions.storage :as storage]
   [publicator.utils.ext :as ext]))

(deftype Transaction [data identity-map]
  storage/Transaction
  (get-many [_ ids]
    (let [ids-for-select (remove #(contains? @identity-map %) ids)
          selected       (->> ids-for-select
                              (select-keys data)
                              (ext/map-vals identity/build))]
      ;; Здесь принципиально использование reverse-merge,
      ;; т.к. другой поток может успеть извлечь данные из базы,
      ;; создать объект-идентичность, записать его в identity map
      ;; и сделать в нем изменения.
      ;; Если использовать merge, то этот поток затрет идентчиность
      ;; другим объектом-идентичностью с начальным состоянием.
      ;; Фактически это нарушает саму идею identity-map -
      ;; сопоставление ссылки на объект с его идентификатором
      (-> identity-map
          (swap! ext/reverse-merge selected)
          (select-keys ids))))

  (create [_ state]
    (let [id     (aggregate/id state)
          istate (identity/build state)]
      (swap! identity-map assoc id istate)
      istate)))

(deftype Storage [db]
  storage/Storage
  (wrap-tx [_ body]
    (loop []
      (let [data         @db
            identity-map (atom {})
            t            (Transaction. data identity-map)
            res          (body t)
            changed      (ext/map-vals deref @identity-map)
            new-data     (merge data changed)]
        (if (compare-and-set! db data new-data)
          res
          (recur))))))

(defn build-db []
  (atom {}))

(defn binding-map [db]
  {#'storage/*storage* (->Storage db)})
