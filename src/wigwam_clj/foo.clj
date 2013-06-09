(ns wigwam-clj.foo
  (:refer-clojure :exclude [defn])
  (:require
    [wigwam-clj.rules   :as r]
    [wigwam-clj.request :as q :refer [defn]]))

(defn login [user pass]
  {:rpc [(r/login! user pass)]
   :pre [(not= user "omfg")]}
  "Congratulations, you're logged in.")

(defn logout []
  {:rpc [(r/logout!)]}
  "Congratulations, you're logged out.")

(defn test1 [x y]
  {:rpc [(r/logged-in?)]}
  (+ x y))

(defn test2 [x y]
  {:rpc [(r/deny)]}
  (- x y))

(defn test3
  "Calls test2 without triggering 'deny' assertion."
  [x y]
  (test2 x y))

(defn ^:private test4
  "Not accessible via RPC (not public)."
  [x y]
  {:rpc [(r/allow)]}
  (* x y))
