(ns wigwam-clj.exception)

(defmulti ex-status   identity)
(defmulti ex-message  identity)
(defmulti ex-severity identity)

(defmacro defex
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

(defex ::csrf     403 :error "There was a problem. Are cookies disabled?")
(defex ::auth     403 :error "Please log in to continue.")
(defex ::ignore   500 :ignore)
(defex ::debug    500 :debug)
(defex ::info     500 :info)
(defex ::notice   500 :notice)
(defex ::warning  500 :warning)
(defex ::error    500 :error)
(defex ::fatal    500 :fatal)

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

(comment
  ;; inheritance: use code and severity of fatal ex
  (defex ::more-fatal ::fatal nil "A most serious error happened.")
  )
