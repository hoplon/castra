(ns wigwam-clj.core
  (:gen-class)
  (:require
    [clojure.pprint     :refer [pprint]]
    [ring.adapter.jetty :refer [run-jetty]]))

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
