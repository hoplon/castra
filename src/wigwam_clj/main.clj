(ns wigwam-clj.main
  (:require
    [ring.adapter.jetty             :as rj]
    [ring.middleware.resource       :as rr]
    [ring.middleware.session        :as rs]
    [ring.middleware.session.cookie :as rk]
    [ring.middleware.file           :as rf]
    [ring.middleware.file-info      :as rg :refer [wrap-file-info]]
    [wigwam-clj.middleware          :as wm :refer [wrap-stacktrace wrap-json wrap-post]]
    [wigwam-clj.core                :as wc]))

(def cookie-store   (rk/cookie-store {:key "a 16-byte secret"}))
(def wrap-session   #(rs/wrap-session % {:store cookie-store}))
(def wrap-resource  #(rr/wrap-resource % "public"))
(def wigwam         (wc/wigwam '[clojure.core :only [inc]] 'wigwam-clj.foo))

(def app
  (-> wigwam
    wrap-stacktrace
    wrap-json
    wrap-session
    wrap-post
    wrap-resource
    wrap-file-info))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ;; work around dangerous default behaviour in Clojure
  (alter-var-root #'*read-eval* (constantly false))
  (rj/run-jetty app {:port 3000}))

