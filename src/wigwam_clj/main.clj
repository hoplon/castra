(ns wigwam-clj.main
  (:require
    [wigwam-clj.core                :as wc]
    [ring.adapter.jetty             :as rj]
    [ring.middleware.session        :as rs]
    [ring.middleware.session.cookie :as rk]))

(def cookie-store (rk/cookie-store {:key "a 16-byte secret"}))
(def wrap-session #(rs/wrap-session % {:store cookie-store}))
(def wigwam       (wc/wigwam '[clojure.core :only [inc]] 'wigwam-clj.foo))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ;; work around dangerous default behaviour in Clojure
  (alter-var-root #'*read-eval* (constantly false))
  (rj/run-jetty (-> wigwam wc/wrap-json wrap-session) {:port 3000}))

