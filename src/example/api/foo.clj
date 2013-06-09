(ns example.api.foo
  (:refer-clojure :exclude [defn])
  (:require
    [example.http.rules        :refer :all]
    [tailrecursion.castra.core :refer [defn]]))

(defn login [user pass]
  {:rpc [(login! user pass)]
   :pre [(not= user "omfg")]}
  "Congratulations, you're logged in.")

(defn logout []
  {:rpc [(logout!)]}
  "Congratulations, you're logged out.")

(defn test1 [x y]
  {:rpc [(logged-in?)]}
  (+ x y))

(defn test2 [x y]
  {:rpc [(deny)]}
  (- x y))

(defn test3
  "Calls test2 without triggering 'deny' assertion."
  [x y]
  (test2 x y))

(defn ^:private test4
  "Not accessible via RPC (not public)."
  [x y]
  {:rpc [(allow)]}
  (* x y))
