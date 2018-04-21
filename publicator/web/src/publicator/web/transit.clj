(ns publicator.web.transit
  (:refer-clojure :exclude [read])
  (:require
   [cognitect.transit :as t])
  (:import
   [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn write [data]
  (let [out    (ByteArrayOutputStream.)
        writer (t/writer out :json)]
    (t/write writer data)
    (.toString out)))

(defn read [s]
  (let [in     (ByteArrayInputStream. (.getBytes s))
        reader (t/reader in :json)]
    (t/read reader)))
