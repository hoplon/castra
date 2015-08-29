(ns castra.core-test
  (:require
    [ring.adapter.jetty :as jetty]
    [castra.core :refer [defrpc ex]]
    [castra.middleware :refer [wrap-castra]]))

(defrpc test1
  [x y z]
  {:pre [(> x 10)]
   :rpc/pre (or (not= z 0)
                (throw (ex "Validation" {:z "Can't be zero."})))
   :rpc/query (/ (* x y) z)}
  (prn :ok-got-here)
  "hi there")

(defn four-oh-four
  [req]
  {:status 404 :body "not found"})

(def app (-> four-oh-four (wrap-castra 'castra.core-test)))

(defonce server (jetty/run-jetty #'app {:join? false :port 4445}))

(defn start [] (.start server))
(defn stop  [] (.stop server))
