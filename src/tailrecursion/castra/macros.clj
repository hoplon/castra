(ns tailrecursion.castra.macros
  (:refer-clojure :exclude [sync]))

(create-ns 'tailrecursion.castra)

(defn remote [async? [f & args] & clauses]
  `(tailrecursion.castra/remote ~async? (pr-str (list '~f ~@args)) ~@clauses))

(defmacro async [& forms]
  (apply remote true forms))

(defmacro sync [& forms]
  (apply remote false forms))
