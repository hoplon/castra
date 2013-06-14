(ns tailrecursion.castra.handler
  (:require
    [ring.middleware.session.cookie :as c]
    [ring.util.response             :as p :refer [charset]]
    [ring.util.codec                :as u :refer [url-decode base64-encode]]
    [clojure.set                    :as s :refer [intersection difference]]
    [tailrecursion.cljson           :as e :refer [cljson->clj clj->cljson]]
    [tailrecursion.castra           :as r :refer [ex ex->clj *request* *session*]]))

(defn csrf! []
  (let [tok1 (get-in @*request* [:headers "x-csrf"])
        tok2 (:x-csrf @*session*)
        tok! #(base64-encode (#'c/secure-random-bytes 16))]
    (when-not (and tok1 (= tok1 tok2))
      (swap! *session* assoc :x-csrf (tok!))
      (throw (ex r/csrf)))))

(defn do-rpc [vars [f & args]]
  (let [bad! #(throw (ex r/fatal (ex r/not-found)))
        fun  (or (resolve (symbol f)) (bad!))]
    (when-not (contains? vars fun) (bad!))
    (when-not (:rpc (meta fun)) (reset! *request* nil)) 
    (apply fun args)))

(defn select-vars [nsname & {:keys [only exclude]}]
  (let [to-var    #(resolve (symbol (str nsname) (str %)))
        to-vars   #(->> % (map to-var) (keep identity) set)
        var-pubs  #(do (require %) (vals (ns-publics %))) 
        vars      (->> nsname var-pubs set)
        only      (if (seq only) (to-vars only) vars)
        exclude   (if (seq exclude) (to-vars exclude) #{})]
    (-> vars (intersection only) (difference exclude))))

(defn castra [& namespaces]
  (let [head {"Content-type" "application/json"}
        seq* #(or (try (seq %) (catch Throwable e)) [%])
        tagx #(let [x (ex->clj %)] (with-meta x {::status (:status x)}))
        vars (->> namespaces (map seq*) (mapcat #(apply select-vars %)) set)]
    (fn [request]
      (if-not (= :post (:request-method request))
        {:status 404 :headers {} :body ""}
        (binding [*request* (atom request), *session* (atom (:session request))]
          (let [f #(do (csrf!) (do-rpc vars (cljson->clj (slurp %))))
                b (try (f (:body request)) (catch Throwable e (tagx e)))
                s (::status (meta b) 200)
                h (assoc head "X-Csrf" (:x-csrf @*session*))]
            (->
              {:status s, :headers h, :body (clj->cljson b), :session @*session*}
              (charset "UTF-8"))))))))
