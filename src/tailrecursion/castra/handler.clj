;; Copyright (c) Alan Dipert and Micha Niskin. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns tailrecursion.castra.handler
  (:require
    [crypto.random                  :as c]
    [ring.util.request              :as q :refer [body-string]]
    [ring.util.codec                :as u :refer [url-decode base64-encode]]
    [clojure.set                    :as s :refer [intersection difference]]
    [tailrecursion.cljson           :as e :refer [cljson->clj clj->cljson]]
    [tailrecursion.castra           :as r :refer [ex ex->clj *request* *session*]]
    [cheshire.core                  :as j :refer [generate-string parse-string]]))

(defn csrf! []
  (let [tok1 (get-in @*request* [:headers "x-castra-csrf"])
        tok! #(c/base64 16)]
    (swap! *session* (fn [x] (update-in x [:x-castra-csrf] #(or % (tok!)))))
    (when-not (and tok1 (= tok1 (:x-castra-csrf @*session*))) (throw (ex r/csrf)))))

(defn do-rpc [vars [f & args]]
  (let [bad!  #(throw (ex r/fatal (ex r/not-found)))
        fun   (or (resolve (symbol f)) (bad!))]
    (when-not (contains? vars fun) (bad!))
    (apply fun args)))

(defn select-vars [nsname & {:keys [only exclude]}]
  (let [to-var    #(resolve (symbol (str nsname) (str %)))
        to-vars   #(->> % (map to-var) (keep identity) set)
        var-pubs  #(do (require %) (vals (ns-publics %))) 
        vars      (->> nsname var-pubs set)
        only      (if (seq only) (to-vars only) vars)
        exclude   (if (seq exclude) (to-vars exclude) #{})]
    (-> vars (intersection only) (difference exclude))))

(defmulti decode-tunnel #(get-in % [:headers "x-castra-tunnel"]))
(defmethod decode-tunnel "cljson" [req] (cljson->clj (body-string req)))
(defmethod decode-tunnel :default [req] (parse-string (body-string req)))

(defmulti encode-tunnel (fn [req x] (get-in req [:headers "x-castra-tunnel"])))
(defmethod encode-tunnel "cljson" [req x] (clj->cljson x))
(defmethod encode-tunnel :default [req x] (generate-string x))

(defn castra [& namespaces]
  (let [head {"Content-type" "application/json"}
        seq* #(or (try (seq %) (catch Throwable e)) [%])
        vars (->> namespaces (map seq*) (mapcat #(apply select-vars %)) set)]
    (fn [req]
      (if-not (= :post (:request-method req))
        {:status 404 :headers {} :body "404 - Not Found"}
        (binding [*request* (atom req), *session* (atom (:session req))]
          (let [f #(do (csrf!) (do-rpc vars (decode-tunnel %)))
                d (try (encode-tunnel req (f req)) (catch Throwable e e))
                x (if (instance? Throwable d) (ex->clj d))
                s (:status x 200)
                b (if x (clj->cljson x) d)
                h (assoc head "X-Castra-Csrf" (:x-castra-csrf @*session*))]
            {:status s, :headers h, :body b, :session @*session*}))))))
