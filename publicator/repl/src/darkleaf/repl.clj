(ns darkleaf.repl
  (:require
   [rebel-readline.main :as rebel]
   [rebel-readline.commands :as commands]
   [clojure.tools.namespace.repl :as ctn.repl]
   [clojure.tools.namespace.find :as ctn.find]
   [clojure.test :as t]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]))

;; ~~~~~~~~~~~~~~~~ reloading ~~~~~~~~~~~~~~~~

(defn before [])
(defn after [])

(defmethod commands/command-doc :repl/reload [_]
  "Reload namespaces.")

(defmethod commands/command :repl/reload [_]
  (before)
  (ctn.repl/refresh :after 'darkleaf.repl/after))

(defmethod commands/command-doc :repl/reload-all [_]
  "Reload all namespaces.")

(defmethod commands/command :repl/reload-all [_]
  (before)
  (ctn.repl/refresh-all :after 'darkleaf.repl/after))

;; ~~~~~~~~~~~~~~~~ testing ~~~~~~~~~~~~~~~~

(defmethod commands/command-doc :repl/run-tests [_]
  "Run all tests in test directory.")

(defmethod commands/command :repl/run-tests [_]
  (let [nses (-> "test"
                 io/file
                 ctn.find/find-namespaces-in-dir)]
    (doseq [ns nses] (require ns))
    (apply t/run-tests nses)))

;; ~~~~~~~~~~~~~~~~ main ~~~~~~~~~~~~~~~~

(defn- make-callback [fn-name]
  (when fn-name
    (fn []
      (let [fn-sym (symbol fn-name)
            _      (require (symbol (namespace fn-sym)))
            fn-var (resolve fn-sym)]
        (assert fn-var (str "Can't resolve " fn-name))
        (fn-var)))))

(defn -main [& {:strs [reload-before-fn reload-after-fn]}]
  (when-some [before (make-callback reload-before-fn)]
    (alter-var-root #'before (fn [_] before)))
  (when-some [after (make-callback reload-after-fn)]
    (alter-var-root #'after (fn [_] after)))
  (rebel/-main))
