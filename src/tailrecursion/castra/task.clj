(ns tailrecursion.castra.task
  (:require
    [tailrecursion.boot.core        :as core]
    [tailrecursion.boot.task.ring   :as r]
    [tailrecursion.castra.handler   :as c]))

(core/deftask castra
  "Add the castra handler to the middleware."
  [& specs]
  (r/ring-task (fn [_] (apply c/castra specs))))

(core/deftask castra-dev-server
  "Creates a server for development with castra. The first argument is
  a quoted namespace or a quoted vector of namespaces with Castra endpoints."
  [namespaces & {:keys [port join? key docroot]
      :or {port    8000
           join?   false
           key     "a 16-byte secret"
           docroot (core/get-env :out-path)}}]
  (comp (r/head) (r/dev-mode) (r/session-cookie key) (r/files docroot)
        (if (coll? namespaces) (apply castra namespaces) (castra namespaces))
        (r/jetty :port port :join? join?)))
