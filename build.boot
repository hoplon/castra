(merge-env!
  :dependencies '[[adzerk/bootlaces           "0.1.11" :scope "test"]
                  [ring/ring                  "1.2.1"  :scope "test"]
                  [org.clojure/clojure        "1.5.1"]
                  [ring/ring-core             "1.2.1"]
                  [com.cognitect/transit-clj  "0.8.281"]
                  [com.cognitect/transit-cljs "0.8.220"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "3.0.0-SNAPSHOT")

(bootlaces! +version+)

(task-options!
  pom {:project      'castra/castra
       :version      +version+
       :description  "HTTP remote procedure call handler for Clojure."
       :url          "https://github.com/hoplon/castra"
       :scm          {:url "https://github.com/hoplon/castra"}
       :license      {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})
