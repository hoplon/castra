(ns wigwam-clj.core
  (:gen-class)
  (:require
    [clojure.string       :as string]
    [clojure.set          :as cs :refer [intersection difference]]
    [ring.util.codec      :as rc]
    [wigwam-clj.exception :as wx]
    [wigwam-clj.request   :as wr :refer [*request* *session*]]
    [tailrecursion.extype :as ex :refer [ex ex->clj]]))

(defn path->sym
  [path]
  (when-let [path (second (re-find #"^(?:/[^/]+)*/([^/]+/[^/]+)$" path))] 
    (-> path rc/url-decode symbol)))

(defn do-rpc
  [vars path args]
  (let [wrapx #(throw (ex wx/fatal (apply ex %&)))
        bad!  #(wrapx wx/not-found)
        err!  #(wrapx wx/error %)
        sym   (or (path->sym path) (bad!))]
    (let [f (or (resolve sym) (bad!))]
      (or (contains? vars f) (bad!))
      (or (:rpc (meta f)) (reset! *request* nil)) 
      (or (vector? args) (err! "Arglist must be a vector."))
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
