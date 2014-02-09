(ns tailrecursion.castra.middleware
  (import java.io.File)
  (:require
   [clojure.java.io    :as io]
   [clojure.java.shell :as sh]))

(def js
  "
var page = require('webpage').create(),
    sys  = require('system'),
    url  = sys.args[1]

page.open(url, function(status) {
  setTimeout(function() {
    var html = page.evaluate(function() {
      return document.documentElement.outerHTML;
    });
    console.log(html);
    phantom.exit();
  }, 0);
});")

(defn phantomjs [js-path url]
  (let [{:keys [exit err out]} (sh/sh "phantomjs" js-path url)]
    (or (and (zero? exit) out)
        (throw (Exception. (format "phantomjs: %s (%d)" err exit))))))

(defn tmp-file [ext content]
  (.getPath (doto (File/createTempFile "tmp-" ext) (spit content))))

(defn wrap-escaped-fragment
  "Middleware to detect Google's '_escaped_fragment_' AJAX crawling requests [1]
  and serve a PhantomJS rendered version of the page.

  NB: This middleware needs to be wrapped in middleware that will provide the
      :params request map key. A suitable candidate for this could be the ring
      #'ring.middleware.params/wrap-params middleware, for instance.

  [1]: https://developers.google.com/webmasters/ajax-crawling/docs/specification"
  [handler]
  (let [scrape (partial phantomjs (tmp-file js))]
    (fn [{{frag "_escaped_fragment_"} :params
          {:strs [host]}              :headers
          :keys [scheme uri request-method] :as req}]
      (let [{:keys [status] :as resp} (handler req)
            err   {:status 500 :body "Server Error"}
            url   (str (name scheme) "://" host uri "#!" frag)
            resp? (not (and frag (= [200 :get] [status request-method])))]
        (if resp? resp (try (assoc resp :body (scrape url)) (catch Throwable _ err)))))))
