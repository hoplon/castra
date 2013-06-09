(ns example.main
  (:require
    [ring.adapter.jetty              :refer [run-jetty]]
    [ring.middleware.resource        :refer [wrap-resource]]
    [ring.middleware.session         :refer [wrap-session]]
    [ring.middleware.session.cookie  :refer [cookie-store]]
    [ring.middleware.file            :refer [wrap-file]]
    [ring.middleware.file-info       :refer [wrap-file-info]]
    [tailrecursion.castra.middleware :refer [wrap-stacktrace wrap-json wrap-post]]
    [tailrecursion.castra.handler    :refer [castra]]))

(def app
  (-> (castra '[clojure.core :only [inc]] 'example.api.foo)
    wrap-stacktrace
    wrap-json
    (wrap-session {:store (cookie-store {:key "a 16-byte secret"})})
    wrap-post
    (wrap-resource "public")
    wrap-file-info))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ;; work around dangerous default behaviour in Clojure
  (alter-var-root #'*read-eval* (constantly false))
  (run-jetty app {:port 3000}))

