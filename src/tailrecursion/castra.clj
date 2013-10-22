;; Copyright (c) Alan Dipert and Micha Niskin. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns tailrecursion.castra
  (:refer-clojure :exclude [defn])
  (:require
    [tailrecursion.extype :as ex :refer [defex extend-ex]]))

(def ^:dynamic *request* (atom nil))
(def ^:dynamic *session* (atom nil))

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

(extend-ex csrf       exception {:status 403} "Invalid or missing CSRF token.")
(extend-ex auth       exception {:status 403} "Authorization required.")
(extend-ex not-found  exception {:status 404} "RPC endpoint not found.")
(extend-ex ignore     exception {:severity :ignore})
(extend-ex debug      exception {:severity :debug})
(extend-ex info       exception {:severity :info})
(extend-ex notice     exception {:severity :notice})
(extend-ex warning    exception {:severity :warning})
(extend-ex error      exception {:severity :error})
(extend-ex fatal      exception {:severity :fatal})

(defn- make-asserts [forms]
  (let [*req* 'tailrecursion.castra/*request*]
    `[(assert (try (if @~*req* (and ~@forms) true)
                (finally (reset! ~*req* nil))))]))

(defmacro defn [name & fdecl]
  (let [doc?  (string? (first fdecl))
        doc   (if doc? [(first fdecl)] [])
        [args & forms] (if doc? (rest fdecl) fdecl)
        pre?  (and (< 1 (count forms)) (map? (first forms))) 
        rpc   (when pre? (make-asserts (:rpc (first forms))))
        head  (->> [(if pre? (dissoc (first forms) :rpc) (first forms))]
                (remove #(or (nil? %) (and pre? (empty? %))))) 
        name  (if rpc (with-meta name (assoc (meta name) :rpc true)) name)]
    `(clojure.core/defn ~name ~@doc ~args ~@head ~@rpc ~@(rest forms))))
