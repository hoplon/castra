(ns wigwam-clj.middleware
  (:require
    [clojure.data.json    :as json]
    [wigwam-clj.exception :as wx]
    [tailrecursion.extype :as ex :refer [ex ex->clj]]))

(defn wrap-post
  [handler]
  (fn [request]
    (if (= :post (:request-method request))
      (handler request)
      {:status 404, :headers {}, :body "404 Not Found"})))

(defn wrap-json
  [handler]
  (fn [request]
    (let [ct {"Content-Type" "application/json"}]
      (try
        (let [data (json/read-str (slurp (:body request)))
              resp (handler (assoc request :body data))]
          (-> resp
            (assoc :body (json/write-str (:body resp)))
            (update-in [:headers] merge ct)))
        (catch Throwable e
          (let [body (json/write-str (ex->clj e wx/fatal))]
            {:status 500, :headers ct, :body body}))))))

