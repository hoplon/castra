(ns wigwam-clj.core
  (:gen-class)
  (:require
    [clojure.string                 :as string]
    [clojure.data.json              :as json]
    [ring.util.codec                :as rc]
    [wigwam-clj.exception           :as wx]
    [wigwam-clj.request             :as wr :refer [*request* *session*]]
    [tailrecursion.extype           :as ex :refer [ex ex->clj]]))

(defn path->sym
  [path]
  (when (re-find #"^/[^/]+/[^/]+$" path)
    (-> path (subs 1) rc/url-decode symbol)))

(defn wrap-json
  [handler]
  (fn [request]
    (let [body (:body request)
          data (json/read-str (if (string? body) body (slurp body)))
          resp (handler (assoc request :body data))]
      (-> resp
        (assoc :body (json/write-str (:body resp)))
        (assoc-in [:headers "Content-Type"] "application/json")))))

(defn do-rpc
  [vars path args]
  (if-not (vector? args)
    (throw (ex wx/fatal (ex wx/error "Arglist must be a vector."))))
  (let [bad! #(throw (ex wx/fatal (ex wx/not-found)))
        sym  (or (path->sym path) (bad!))]
    (require (symbol (namespace sym)))
    (let [f (or (resolve sym) (bad!))]
      (or (contains? vars f) (bad!))
      (if-not (:rpc (meta f)) (reset! *request* nil)) 
      (apply f args))))

(defn select-vars
  [nsname & {:keys [only exclude]}]
  (let [var-pubs  #(do (require %) (vals (ns-publics %)))
        to-var    #(resolve (symbol (str nsname) (str %)))
        to-vars   #(keep identity (map to-var %))
        var-fn?   #(fn? (var-get %))
        test?     #(fn [x] (if (seq %1) (contains? (set %) x) %2))]
    (->> (var-pubs nsname)
      (filter (test? (to-vars only) true))
      (remove (test? (to-vars exclude) false)))))

(defn wigwam
  [& namespaces]
  (let [seq* #(or (try (seq %) (catch Throwable e)) [%])
        vars (->> namespaces (map seq*) (mapcat #(apply select-vars %)) set)]
    (fn [request]
      (binding [*request* (atom request)
                *session* (atom (:session request))]
        (let [resp (try
                     (do-rpc vars (:uri request) (:body request))
                     (catch Throwable e e))
              xclj (when (instance? Throwable resp) (ex->clj resp wx/fatal))]
          {:status  (or (:status xclj) 200)
           :body    (or xclj resp)
           :session @*session*})))))
