(ns wigwam-clj.request)

(def ^:dynamic *request* nil)

(defmacro when-http
  [form]
  `(if wigwam-clj.request/*request* ~form true))
