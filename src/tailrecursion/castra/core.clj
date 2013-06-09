(ns tailrecursion.castra.core
  (:refer-clojure :exclude [defn]))

(def ^:dynamic *request* (atom nil))
(def ^:dynamic *session* (atom nil))

(defn- make-asserts [forms]
  (when forms `[(assert (tailrecursion.castra.core/when-http ~forms))]))

(defmacro when-http [forms]
  `(try
     (if @tailrecursion.castra.core/*request* (and ~@forms) true)
     (finally (reset! tailrecursion.castra.core/*request* nil))))

(defmacro defn [name & fdecl]
  (let [doc?  (string? (first fdecl))
        doc   (if doc? [(first fdecl)] [])
        [args & forms] (if doc? (rest fdecl) fdecl)
        pre?  (and (< 1 (count forms)) (map? (first forms))) 
        rpc   (when pre? (make-asserts (:rpc (first forms))))
        head  (->> [(if pre? (dissoc (first forms) :rpc) (first forms))]
                (remove #(or (nil? %) (and pre? (empty? %))))) 
        name  (if rpc (with-meta name (assoc (meta name) :rpc true)) name)]
    `(clojure.core/defn ~name ~@doc ~args ~@head ~@rpc ~@(rest forms))))
