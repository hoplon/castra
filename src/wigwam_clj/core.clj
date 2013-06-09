(ns wigwam-clj.core
  (:require
    [ring.util.codec      :as rc]
    [clojure.string       :as string]
    [clojure.set          :as cs :refer [intersection difference]]
    [wigwam-clj.exception :as wx :refer [ex ex->clj]]
    [wigwam-clj.request   :as wr :refer [*request* *session*]]))

(defn path->sym [path]
  (when-let [path (second (re-find #"^(?:/[^/]+)*/([^/]+/[^/]+)$" path))] 
    (-> path rc/url-decode symbol)))

(defn do-rpc [vars path args]
  (let [bad! #(throw (ex wx/fatal (apply ex wx/not-found)))
        sym  (or (path->sym path) (bad!))]
    (let [f (or (resolve sym) (bad!))]
      (or (contains? vars f) (bad!))
      (or (:rpc (meta f)) (reset! *request* nil)) 
      (apply f args))))

(defn select-vars [nsname & {:keys [only exclude]}]
  (let [var-fn?   #(fn? (var-get %))
        to-var    #(resolve (symbol (str nsname) (str %)))
        to-vars   #(->> % (map to-var) (keep identity) set)
        var-pubs  #(do (require %) (vals (ns-publics %))) 
        vars      (->> nsname var-pubs (filter var-fn?) set)
        only      (if (seq only) (to-vars only) vars)
        exclude   (if (seq exclude) (to-vars exclude) #{})]
    (-> vars (intersection only) (difference exclude))))

(defn wigwam [& namespaces]
  (let [seq* #(or (try (seq %) (catch Throwable e)) [%])
        vars (->> namespaces (map seq*) (mapcat #(apply select-vars %)) set)]
    (fn [request]
      (binding [*request* (atom request), *session* (atom (:session request))]
        (let [d #(do-rpc vars (:uri request) (:body request))
              r (try (d) (catch Throwable e e))
              x (when (instance? Throwable r) (ex->clj r))]
          {:status (or (:status x) 200), :body (or x r), :session @*session*})))))
