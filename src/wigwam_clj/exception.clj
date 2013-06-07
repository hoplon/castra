(ns wigwam-clj.exception)

(defmulti ex-status   identity)
(defmulti ex-message  identity)
(defmulti ex-severity identity)

(defmacro defstatus
  [type code & [severity message]]
  (let [ex?    (isa? code ::exception)
        class* (if ex? code ::exception)
        code*  (if ex? (ex-status code) code)
        msg*   (or message (if ex? (ex-message code) "Internal server error."))
        svr*   (or severity (if ex? (ex-severity code) :error))]
    `(do
       (derive ~type ~class*)
       (defmethod ex-status ~type [_#] ~code*)
       (defmethod ex-message ~type [_#] ~msg*)
       (defmethod ex-severity ~type [_#] ~svr*))))

(defstatus ::csrf     403 :error "Bad CSRF token.")
(defstatus ::auth     403 :error "Please log in to continue.")
(defstatus ::ignore   500 :ignore)
(defstatus ::debug    500 :debug)
(defstatus ::info     500 :info)
(defstatus ::notice   500 :notice)
(defstatus ::warning  500 :warning)
(defstatus ::error    500 :error)
(defstatus ::fatal    500 :fatal)

(defn ex
  "Create new wigwam exception."
  [type & [message data cause]]
  (let [m (or message (ex-message type))
        d {:type      type
           :data      data}]
    (ex-info m d cause)))

(defn ex->clj
  "Get exception properties and data as a clj map."
  [e]
  (let [e (if (isa? (:type (ex-data e)) ::exception) e (ex ::fatal nil nil e))
        p (ex-data e)
        t (:type p)
        a (ancestors t)
        m (.getMessage e)
        s (ex-severity t)
        d (:data p)
        c (some-> (.getCause e) .getMessage)]
    {:type t :isa a :message m :severity s :data d :cause c}))

