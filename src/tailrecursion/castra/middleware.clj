(ns tailrecursion.castra.middleware
  (:require
    [clojure.data.json              :as j]
    [tailrecursion.cljson           :as k]
    [tailrecursion.castra.exception :as x :refer [ex->clj]]))

(defn make-wrap [reader writer content-type]
  (fn [handler]
    (fn [request]
      (if-not (= :post (:request-method request))
        {:status 404 :headers {} :body ""}
        (let [ct {"Content-Type" content-type}
              rx (fn [x] {:status 500, :headers ct, :body (writer (ex->clj x))})
              js #(let [data (reader (slurp (:body request)))
                        resp (handler (assoc request :body data))]
                    (-> resp
                      (assoc :body (writer (:body resp)))
                      (update-in [:headers] merge ct)))]
          (try (js) (catch Throwable e (rx e)))))))) 

(def wrap-json    (make-wrap j/read-str j/write-str "application/json"))
(def wrap-cljson  (make-wrap k/read-str k/write-str "application/json"))
