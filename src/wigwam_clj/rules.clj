(ns wigwam-clj.rules
  (:require
    [wigwam-clj.exception :as wx  :refer [ex auth]]
    [wigwam-clj.request   :as rpc :refer [*request* *session*]]))

(def allow (constantly true))
(def deny  #(throw (ex auth "You're not allowed to do that.")))

(defn login! [login passwd]
  (if (= passwd "foop")
    (swap! *session* assoc :login login)
    (throw (ex auth "Incorrect username or password."))))

(defn logout! []
  (swap! *session* assoc :login nil))

(defn logged-in? []
  (or (get @*session* :login) (throw (ex auth))))
