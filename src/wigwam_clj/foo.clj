(ns wigwam-clj.foo
  (:require
    [clojure.pprint       :as pp]
    [wigwam-clj.exception :as wx]
    [tailrecursion.extype :as ex  :refer [ex]]
    [wigwam-clj.request   :as rpc :refer [*request*]]))

;; rules ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def allow (constantly true))
(def deny  #(throw (ex wx/auth "You're not allowed to do that.")))

(defn login! [login passwd]
  (if (= passwd "foop")
    (swap! *request* assoc-in [:session :login] login)
    (throw (ex wx/auth "Incorrect username or password."))))

(defn logout! []
  (swap! *request* assoc :session nil))

(defn logged-in? []
  (pp/pprint (get @*request* :session))
  (or (get-in @*request* [:session :login]) (throw (ex wx/auth))))

;; API methods ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(rpc/defn login [user pass]
  {:rpc [(login! user pass)]
   :pre [(not= user "omfg")]}
  "Congratulations, you're logged in.")

(rpc/defn logout []
  {:rpc [(logout!)]}
  "Congratulations, you're logged out.")

(rpc/defn test1 [x y]
  {:rpc [(logged-in?)]}
  (+ x y))

(rpc/defn test2 [x y]
  {:rpc [(deny)]}
  (- x y))

(rpc/defn test3 [x y]
  {:rpc [(allow)]}
  (test2 x y))
