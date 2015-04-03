(merge-env!
  :dependencies '[[adzerk/bootlaces        "0.1.11" :scope "test"]
                  [org.clojure/clojure     "1.5.1"]
                  [cheshire                "5.2.0"]
                  [ring/ring-core          "1.2.1"]
                  [tailrecursion/cljson    "1.0.6"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "3.0.0-SNAPSHOT")

(bootlaces! +version+)

(task-options!
  pom {:project      'tailrecursion/castra
       :version      +version+
       :description  "HTTP remote procedure call handler for Clojure."
       :url          "https://github.com/tailrecursion/castra"
       :scm          {:url "https://github.com/tailrecursion/castra"}
       :license      {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})
