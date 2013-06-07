(ns wigwam-clj.exception)

(defmulti ex-status   identity)
(defmulti ex-message  identity)
(defmulti ex-severity identity)

(defn find-first [pred? coll] (first (filter pred? coll)))

(defmacro defex
  [type code & more]
  (let [message   (find-first string? more)
        severity  (find-first keyword? more)
        ex?       (isa? code ::exception)
        class*    (if ex? code ::exception)
        code*     (if ex? (ex-status code) code)
        msg*      (or message (if ex? (ex-message code) "Internal server error."))
        svr*      (or severity (if ex? (ex-severity code) :error))]
    `(do
       (derive ~type ~class*)
       (defmethod ex-status ~type [_#] ~code*)
       (defmethod ex-message ~type [_#] ~msg*)
       (defmethod ex-severity ~type [_#] ~svr*))))

(defex ::csrf     403 "There was a problem. Are cookies disabled?")
(defex ::auth     403 "Please log in to continue.")
(defex ::ignore   500 :ignore)
(defex ::debug    500 :debug)
(defex ::info     500 :info)
(defex ::notice   500 :notice)
(defex ::warning  500 :warning)
(defex ::error    500 :error)
(defex ::fatal    500 :fatal)

(defn ex
  "Create new wigwam exception."
  [type & more]
  (let [message (find-first string? more)
        data    (find-first map? more)
        cause   (find-first (partial instance? Throwable) more)
        m (or message (ex-message type))
        d {:type      type
           :data      data}]
    (ex-info m d cause)))

(defn ex->clj
  "Get exception properties and data as a clj map."
  [e]
  (let [e (if (isa? (:type (ex-data e)) ::exception) e (ex ::fatal e))
        p (ex-data e)
        t (:type p)
        a (ancestors t)
        m (.getMessage e)
        s (ex-severity t)
        d (:data p)
        c (some-> (.getCause e) .getMessage)]
    {:type t :isa a :message m :severity s :data d :cause c}))

(comment

  ;; inheritance: use code and severity of fatal ex, but not message
  (defex ::more-fatal ::fatal "A most serious error happened.")

  ;; use code and message of fatal ex, but not severity
  (defex ::less-fatal ::fatal :warning)
  )
