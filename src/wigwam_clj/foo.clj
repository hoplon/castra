(ns wigwam-clj.foo
  (:require
    [clojure.pprint       :as pp]
    [wigwam-clj.exception :as wx  :refer [ex]]
    [wigwam-clj.request   :as rpc :refer [*request* *session*]]))

;; rules ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def allow (constantly true))
(def deny  #(throw (ex wx/auth "You're not allowed to do that.")))

(defn login! [login passwd]
  (if (= passwd "foop")
    (swap! *session* assoc :login login)
    (throw (ex wx/auth "Incorrect username or password."))))

(defn logout! []
  (swap! *session* assoc :login nil))

(defn logged-in? []
  (pp/pprint @*session*)
  (or (get @*session* :login) (throw (ex wx/auth))))

;; API methods ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(rpc/defn login
  "This is a doc comment."
  [user pass]
  {:rpc [(login! user pass)]
   :pre [(not= user "omfg")]}
  "Congratulations, you're logged in.")

(rpc/defn ^:rpc-test1 logout
  "Hello world."
  []
  {:rpc [(logout!)]}
  "Congratulations, you're logged out.")

(rpc/defn test1
  "The rain in spain falls mainly on the plain."
  [x y]
  {:rpc [(logged-in?)]}
  (+ x y))

(rpc/defn test2 [x y]
  {:rpc [(deny)]}
  (- x y))

(rpc/defn test3 [x y]
  {:rpc [(allow)]}
  (test2 x y))
