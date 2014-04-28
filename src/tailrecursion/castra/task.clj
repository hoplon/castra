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
  "Creates a server for development with castra."
  [& namespaces]
  (comp (r/head) (r/dev-mode) (r/session-cookie) (r/files)
        (apply castra namespaces) (r/jetty)))
