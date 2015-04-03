;; Copyright (c) Alan Dipert and Micha Niskin. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns tailrecursion.castra
  )

(def ^:dynamic *request* (atom nil))
(def ^:dynamic *session* (atom nil))
(def ^:dynamic *validate-only* nil)

(defn ex [message data & [cause]]
  (ex-info message (assoc data ::exception true) cause))

(defn dfl-ex [e]
  (ex "Server error." {} e))

(defn ex? [e]
  (::exception (ex-data e)))

(defn- make-arity [[bind & [m & body :as forms]]]
  (if (not (and (< 1 (count forms)) (map? m)))
    (make-arity (into [bind {}] (if (seq forms) forms [nil])))
    (let [{pre :rpc/pre query :rpc/query} m
          pre (when pre [pre])
          qry (when query [query])
          m' (not-empty (dissoc m :rpc/pre :rpc/query))]
      `(~bind
         ~@(when m' [m'])
         (let [req# @*request*]
           (reset! *request* nil)
           ~@pre
           (when (not *validate-only*)
             (if-not req#
               (do ~@body)
               (do ~@body ~@qry))))))))

(defmacro defrpc [name & fdecl]
  (let [[_ name [_ & arities]]
        (macroexpand-1 `(clojure.core/defn ~name ~@fdecl))]
    `(def ~name (fn ~@(map make-arity arities)))))
