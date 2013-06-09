(ns wigwam-clj.exception
  (:require
    [tailrecursion.extype :as ex :refer [defex extend-ex]]))

(def exception  ::exception)
(def csrf       ::csrf)
(def auth       ::auth)
(def not-found  ::not-found)
(def ignore     ::ignore)
(def debug      ::debug)
(def info       ::info)
(def notice     ::notice)
(def warning    ::warning)
(def error      ::error)
(def fatal      ::fatal)

(def ex         ex/ex)
(def ex->clj    #(ex/ex->clj % fatal))

(defex exception "Server error." {:status 500 :severity :error})

(extend-ex csrf       exception {:status 403} "There was a problem. Are cookies disabled?")
(extend-ex auth       exception {:status 403} "Please log in to continue.")
(extend-ex not-found  exception {:status 404} "RPC endpoint not found.")
(extend-ex ignore     exception {:severity :ignore})
(extend-ex debug      exception {:severity :debug})
(extend-ex info       exception {:severity :info})
(extend-ex notice     exception {:severity :notice})
(extend-ex warning    exception {:severity :warning})
(extend-ex error      exception {:severity :error})
(extend-ex fatal      exception {:severity :fatal})
