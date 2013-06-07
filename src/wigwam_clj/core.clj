(ns wigwam-clj.core
  (:gen-class)
  (:require
    [tailrecursion.extype   :as ex]
    [wigwam-clj.exception   :as w]
    [clojure.pprint         :as pp  :refer [pprint]]
    [ring.adapter.jetty     :as jt  :refer [run-jetty]]))

(ex/extend-ex ::foop w/ignore "Foop happens.")

(defn doit []
  (throw (ex/ex ::foop "hello world")))

(defn pp
  [form]
  (with-out-str
    (binding [*print-meta* true]
      (pprint form))))

(defn handler
  [request]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (pp request)})

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ;; work around dangerous default behaviour in Clojure
  (alter-var-root #'*read-eval* (constantly false))
  (run-jetty handler {:port 3000}))
