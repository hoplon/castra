(ns tailrecursion.castra.middleware
  (:require
    [clojure.data.json              :as j]
    [tailrecursion.castra.exception :as x :refer [ex->clj]]))

(defn wrap-post [handler]
  (fn [request]
    (if (= :post (:request-method request))
      (handler request)
      {:status 404, :headers {}, :body "404 Not Found"})))

(defn wrap-stacktrace [handler]
  (fn [request]
    (let [resp (handler request)]
      (when (not= 200 (:status resp))
        (print (:trace (:body resp)))
        (flush))
      resp)))

(defn wrap-json [handler]
  (fn [request]
    (let [ct {"Content-Type" "application/json"}
          rx (fn [x] {:status 500, :headers ct, :body (j/write-str (ex->clj x))})
          js #(let [data (j/read-str (slurp (:body request)))
                    resp (handler (assoc request :body data))]
                (-> resp
                  (assoc :body (j/write-str (:body resp)))
                  (update-in [:headers] merge ct)))]
      (try (js) (catch Throwable e (rx e))))))
