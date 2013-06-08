(ns wigwam-clj.core
  (:gen-class)
  (:require
    [clojure.data.json              :as json]
    [clojure.string                 :as string]
    [tailrecursion.extype           :as ex :refer [ex ex->clj]]
    [wigwam-clj.exception           :as wx]
    [wigwam-clj.request             :as wr :refer [*request*]]
    [ring.util.codec                :as rc]
    [ring.adapter.jetty             :as rj]
    [ring.middleware.session        :as rs :refer [wrap-session]]
    [ring.middleware.session.cookie :as rk :refer [cookie-store]]
    [clojure.pprint                 :as pp]
    ))

(defn pp [form] (with-out-str (binding [*print-meta* true] (pp/pprint form))))
(defmacro guard [form] `(try ~form (catch Throwable e)))

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
  [path args]
  (if-not (vector? args)
    (throw (ex wx/fatal "Arglist must be a vector.")))
  (if-let [sym (path->sym path)]
    (do
      (require (symbol (namespace sym))) 
      (if-let [f (resolve sym)]
        (apply f args)
        (throw (ex wx/fatal "Can't resolve var.")))) 
    (throw (ex wx/fatal "Invalid symbol."))))

(defn wigwam
  [request]
  (binding [*request* (atom request)]
    (let [resp (try
                 (do-rpc (:uri request) (:body request))
                 (catch Throwable e e))
          ex?  (instance? Throwable resp)
          xclj (when ex? (ex->clj resp wx/fatal))
          code (or (:status xclj) 200)
          body (or xclj resp)
          sess (get @*request* :session {})]
      {:status code, :body body, :session sess})))

(def app (-> wigwam wrap-json (wrap-session {:store (cookie-store {:key "a 16-byte secret"})})))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ;; work around dangerous default behaviour in Clojure
  (alter-var-root #'*read-eval* (constantly false))
  (rj/run-jetty app {:port 3000}))
