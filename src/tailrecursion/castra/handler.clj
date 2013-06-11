(ns tailrecursion.castra.handler
  (:require
    [ring.middleware.session.cookie   :as c]
    [ring.util.codec                  :as u :refer [url-decode base64-encode]]
    [clojure.set                      :as s :refer [intersection difference]]
    [clojure.tools.reader.edn         :as e :refer [read-string]]
    [tailrecursion.castra.exception   :as x :refer [ex ex->clj]]
    [tailrecursion.castra.core        :as r :refer [*request* *session*]]))

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
  (let [to-var    #(resolve (symbol (str nsname) (str %)))
        to-vars   #(->> % (map to-var) (keep identity) set)
        var-pubs  #(do (require %) (vals (ns-publics %))) 
        vars      (->> nsname var-pubs set)
        only      (if (seq only) (to-vars only) vars)
        exclude   (if (seq exclude) (to-vars exclude) #{})]
    (-> vars (intersection only) (difference exclude))))

(defn castra [& namespaces]
  (let [head {"Content-type" "application/edn"}
        seq* #(or (try (seq %) (catch Throwable e)) [%])
        tagx #(let [x (ex->clj %)] (with-meta x {::status (:status x)}))
        vars (->> namespaces (map seq*) (mapcat #(apply select-vars %)) set)]
    (fn [request]
      (if-not (= :post (:request-method request))
        {:status 404 :headers {} :body ""}
        (binding [*request* (atom request), *session* (atom (:session request))]
          (let [f #(do (csrf!) (do-rpc vars (read-string %)))
                b (try (f (:body request)) (catch Throwable e (tagx e)))
                s (::status (meta b) 200)
                h (assoc head "X-Csrf" (:x-csrf @*session*))]
            {:status s, :headers h, :body (pr-str b), :session @*session*}))))))
