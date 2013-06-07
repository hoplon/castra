(ns wigwam-clj.exception)

(defmulti extype identity)

(defn find-first [pred? coll] (first (filter pred? coll)))

(defmacro defex [type message m]
  `(defmethod extype ~type [_#] ~{::message message ::data m}))

(defmacro extend-ex
  [type parent & more]
  (let [message (find-first string? more)
        data    (or (find-first map? more) {}) 
        ext     (extype parent)
        message (or message (::message ext))
        data    (merge (::data ext) data)]
    `(do
       (defex ~type ~message ~data)
       (derive ~type ~parent))))

(defn ex
  "Create new wigwam exception."
  [type & more]
  (let [message (or (find-first string? more) (::message (extype type))) 
        data    (find-first map? more)
        cause   (find-first (partial instance? Throwable) more)]
    (ex-info message {::type type ::data data} cause)))

(defn ex->clj
  "Get exception properties and data as a clj map."
  [e & [dfl]]
  (if-let [e (cond (::type (ex-data e)) e dfl (ex dfl e))]
    (let [p  (ex-data e)
          t  (::type p)
          a  (ancestors t)
          m  (.getMessage e)
          d  (::data p)
          c  (loop [cx (.getCause e), cc []]
               (if cx (recur (.getCause cx) (conj cc (.getMessage cx))) cc))
          td (::data (extype t))]
      (into {:type t :isa a :message m :data d :cause c} td))))

(defex ::exception "Internal server error." {:status 500 :severity :error})

(extend-ex ::csrf    ::exception {:status 403} "There was a problem. Are cookies disabled?")
(extend-ex ::auth    ::exception {:status 403} "Please log in to continue.")
(extend-ex ::ignore  ::exception {:severity :ignore})
(extend-ex ::debug   ::exception {:severity :debug})
(extend-ex ::info    ::exception {:severity :info})
(extend-ex ::notice  ::exception {:severity :notice})
(extend-ex ::warning ::exception {:severity :warning})
(extend-ex ::error   ::exception {:severity :error})
(extend-ex ::fatal   ::exception {:severity :fatal})

(comment

  ;; create base exception type
  (defex ::exception "Internal server error." {:status 500, :severity :error})

  ;; extend ::exception
  (extend-ex ::not-found ::exception "Not found." {:status 404})

  ;; extend ::not-found
  (extend-ex ::no-such-rpc-method ::not-found "RPC endpoint doesn't exist.")

  ;; throw an exception
  (throw (ex ::no-such-rpc-method))
  (throw (ex ::no-such-rpc-method {:thing 1, :other 2}))
  (throw (ex ::no-such-rpc-method "RPC failed without data."))
  (throw (ex ::no-such-rpc-method "RPC failed with data." {:thing 1, :other 2}))

  ;; get clj map representation of exception
  (ex->clj (ex ::no-such-rpc-method {:thing 1, :other 2}))
  ;;=> {:type     :user/no-such-rpc-method
  ;;    :isa      #{:user/not-found :user/exception}
  ;;    :message  "RPC endpoint doesn't exist."
  ;;    :data     {:thing 1, :other 2}
  ;;    :cause    nil
  ;;    :status   404
  ;;    :severity :error}

  )
