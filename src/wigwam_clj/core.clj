(ns wigwam-clj.core
  (:gen-class)
  (:require
    [clojure.string       :as string]
    [clojure.data.json    :as json]
    [clojure.set          :as cs :refer [intersection difference]]
    [ring.util.codec      :as rc]
    [wigwam-clj.exception :as wx]
    [wigwam-clj.request   :as wr :refer [*request* *session*]]
    [tailrecursion.extype :as ex :refer [ex ex->clj]]))

(defn path->sym
  [path]
  (when-let [path (second (re-find #"^(?:/[^/]+)*/([^/]+/[^/]+)$" path))] 
    (-> path rc/url-decode symbol)))

(defn wrap-post
  [handler]
  (fn [request]
    (if (= :post (:request-method request))
      (handler request)
      {:status 404 :headers {} :body ""})))

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
  (let [var-fn?   #(fn? (var-get %))
        to-var    #(resolve (symbol (str nsname) (str %)))
        to-vars   #(->> % (map to-var) (keep identity) set)
        var-pubs  #(do (require %) (vals (ns-publics %))) 
        vars      (->> nsname var-pubs (filter var-fn?) set)
        only      (if (seq only) (to-vars only) vars)
        exclude   (if (seq exclude) (to-vars exclude) #{})]
    (-> vars (intersection only) (difference exclude))))

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
