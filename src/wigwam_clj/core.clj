(ns wigwam-clj.core
  (:require
    [ring.middleware.session.cookie :as rc]
    [ring.util.codec      :as ru :refer [url-decode base64-encode]]
    [clojure.set          :as cs :refer [intersection difference]]
    [wigwam-clj.exception :as wx :refer [ex ex->clj]]
    [wigwam-clj.request   :as wr :refer [*request* *session*]]))

(defn path->sym [path]
  (when-let [path (second (re-find #"^(?:/[^/]+)*/([^/]+/[^/]+)$" path))] 
    (-> path url-decode symbol)))

(defn csrf! []
  (let [tok1 (get-in @*request* [:headers "x-csrf"])
        tok2 (:x-csrf @*session*)
        tok! #(base64-encode (#'rc/secure-random-bytes 16))]
    (when-not (and tok1 (= tok1 tok2))
      (swap! *session* assoc :x-csrf (tok!))
      (throw (ex wx/csrf)))))

(defn do-rpc [vars path args]
  (let [bad! #(throw (ex wx/fatal (ex wx/not-found)))
        sym  (or (path->sym path) (bad!))
        fun  (or (resolve sym) (bad!))]
    (when-not (contains? vars fun) (bad!))
    (when-not (:rpc (meta fun)) (reset! *request* nil)) 
    (apply fun args)))

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
        (let [d #(do (csrf!) (do-rpc vars (:uri request) (:body request))) 
              r (try (d) (catch Throwable e e))
              x (when (instance? Throwable r) (ex->clj r))
              h {"X-Csrf" (:x-csrf @*session*)}]
          {:status (:status x 200), :headers h, :body (or x r), :session @*session*})))))
