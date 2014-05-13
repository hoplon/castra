(defproject tailrecursion/castra "1.3.0"
  :description  "HTTP remote procedure call handler for Clojure."
  :url          "http://example.com/FIXME"
  :license      {:name "Eclipse Public License"
                 :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure     "1.5.1"]
                 [cheshire                "5.2.0"]
                 [ring/ring-core          "1.2.1"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [tailrecursion/cljson    "1.0.6"]
                 [tailrecursion/extype    "0.1.0"]
                 [tailrecursion/boot.ring "0.2.0"]])
