(ns tailrecursion.castra.macros
  (:require
    [clojure.walk :refer [postwalk]]
    )
  )

(defmacro require-remote [& refs]
  (let [sym   #(symbol (str %1) (str %2))
        mta   (fn [x y] `(with-meta #{::remote} {::remote '~(sym x y)}))
        rmt   (fn [x y] `(def ~y ~(mta x y)))
        rns   (fn [x & {y :refer}] `(do ~@(map #(rmt x %) y)))]
    `(do ~@(map (partial apply rns) refs))))

(comment

  (def foo 100)
  (def bar "omfg")

  (try-async state
    (swap! loading conj true)
    (server.ns/doit foo bar)
    (catch server.ns/auth e error
      (:message e))
    (finally (swap! loading peek)))

(defmacro try-async [& clauses]
  (let [exprs (remove #(contains? #{'catch 'finally} (first %)) clauses)
        ctchs (filter #(= 'catch (first %)) clauses)
        finly (first (filter #(= 'finally (first %) clauses)))
        
        ]
    
    )
  )
  
  ) 
