(ns wigwam-clj.foo
  (:require
    [clojure.pprint       :as pp]
    [wigwam-clj.exception :as wx]
    [wigwam-clj.request   :as wr :refer [when-http *request*]]
    [tailrecursion.extype :as ex :refer [ex]]))

;; rules ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn login!
  [login passwd]
  (if (= passwd "foop")
    (swap! *request* (fnil assoc-in {}) [:session :login] login)
    (throw (ex wx/auth "Incorrect username or password."))))

(defn logged-in?
  []
  (or (get-in @*request* [:session :login]) (throw (ex wx/auth))))

;; API methods ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn login
  [user pass]
  {:pre [(when-http (login! user pass))]}
  "Congratulations, you're logged in.")

(defn test1 [x y]
  {:pre [(when-http (logged-in?))]}
  (+ x y))

