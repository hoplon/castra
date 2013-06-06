(ns wigwam-clj.exception)

(def classes
  {1 ::informational
   2 ::successful
   3 ::redirection
   4 ::client-error
   5 ::server-error})

(defmulti ex-status identity)
(defmulti ex-message identity)

(defmacro defstatus [type code message]
  (let [cls (classes (int (/ code 100)))]
    `(do
       (derive ~type ~cls)
       (defmethod ex-status ~type [_#] ~code)
       (defmethod ex-message ~type [_#] ~message))))

(defstatus ::ok                     200 "OK")
(defstatus ::bad-request            400 "Bad Request")
(defstatus ::internal-server-error  500 "Internal Server Error")

(defn ex [status & {:keys [message data headers body cause]}]
  (let [d {:status (ex-status status) :data data :headers headers :body body}]
    (ex-info (or message (ex-message status)) d cause)))

;; exceptions

(defn ok [body]
  (ex ::ok :body body))

(defn error [& [message & [data & [cause]]]]
  (ex ::internal-server-error :message message :data data :cause cause))
