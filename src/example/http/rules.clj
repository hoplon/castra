(ns example.http.rules
  (:require
    [tailrecursion.castra.exception :refer [ex auth]]
    [tailrecursion.castra.core      :refer [*request* *session*]]))

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
