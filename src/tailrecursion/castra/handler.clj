(ns tailrecursion.castra.handler
  (:require
    [ring.middleware.session.cookie :as c]
    [ring.util.codec                :as u :refer [url-decode base64-encode]]
    [clojure.set                    :as s :refer [intersection difference]]
    [tailrecursion.castra.exception :as x :refer [ex ex->clj]]
    [tailrecursion.castra.core      :as r :refer [*request* *session*]]))

(defn csrf! []
  (let [tok1 (get-in @*request* [:headers "x-csrf"])
        tok2 (:x-csrf @*session*)
        tok! #(base64-encode (#'c/secure-random-bytes 16))]
    (when-not (and tok1 (= tok1 tok2))
      (swap! *session* assoc :x-csrf (tok!))
      (throw (ex x/csrf)))))

(defn do-rpc [vars [f & args]]
  (let [bad! #(throw (ex x/fatal (ex x/not-found)))
        fun  (or (resolve (symbol f)) (bad!))]
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

(defn castra [& namespaces]
  (let [seq* #(or (try (seq %) (catch Throwable e)) [%])
        vars (->> namespaces (map seq*) (mapcat #(apply select-vars %)) set)]
    (fn [request]
      (binding [*request* (atom request), *session* (atom (:session request))]
        (let [d #(do (csrf!) (do-rpc vars (:body request))) 
              r (try (d) (catch Throwable e e))
              x (when (instance? Throwable r) (ex->clj r))
              h {"X-Csrf" (:x-csrf @*session*)}]
          {:status (:status x 200), :headers h, :body (or x r), :session @*session*})))))
