(ns tailrecursion.castra.middleware
  (import java.io.File)
  (:require
    [clojure.java.io      :as io]
    [clojure.java.shell   :as sh]
    [ring.util.request    :as q :refer [body-string]]
    [ring.util.codec      :as u :refer [url-decode base64-encode]]
    [clojure.set          :as s :refer [intersection difference]]
    [tailrecursion.cljson :as e :refer [cljson->clj clj->cljson]]
    [tailrecursion.castra :as r :refer [ex ex? dfl-ex *request* *session*]]
    [cheshire.core        :as j :refer [generate-string parse-string]]
    [clojure.stacktrace   :as t :refer [print-cause-trace print-stack-trace]]))

(defn- ex->clj [e]
  (let [e (if (ex? e) e (dfl-ex e))]
    {:message (.getMessage e)
     :data    (ex-data e)
     :stack   (with-out-str
                (try (print-cause-trace e)
                     (catch Throwable x
                       (try (print-stack-trace e)
                            (catch Throwable x
                              (printf "No stack trace: %s" (.getMessage x)))))))}))

(defn- csrf! []
  (when-not (get-in @*request* [:headers "x-castra-csrf"])
    (throw (ex-info "Invalid CSRF token" {}))))

(defn- do-rpc [vars [f & args]]
  (let [bad!  #(throw (ex-info "RPC endpoint not found" {:endpoint (symbol f)}))
        fun   (or (resolve (symbol f)) (bad!))]
    (when-not (contains? vars fun) (bad!))
    (apply fun args)))

(defn- select-vars [nsname & {:keys [only exclude]}]
  (let [to-var    #(resolve (symbol (str nsname) (str %)))
        to-vars   #(->> % (map to-var) (keep identity) set)
        var-pubs  #(do (require %) (vals (ns-publics %)))
        vars      (->> nsname var-pubs set)
        only      (if (seq only) (to-vars only) vars)
        exclude   (if (seq exclude) (to-vars exclude) #{})]
    (-> vars (intersection only) (difference exclude))))

(defmulti  decode-tunnel #(get-in % [:headers "x-castra-tunnel"]))
(defmethod decode-tunnel "cljson" [req] (cljson->clj (body-string req)))
(defmethod decode-tunnel :default [req] (parse-string (body-string req)))

(defmulti  encode-tunnel (fn [req x] (get-in req [:headers "x-castra-tunnel"])))
(defmethod encode-tunnel "cljson" [req x] (clj->cljson x))
(defmethod encode-tunnel :default [req x] (generate-string x))

(defn wrap-castra [handler & namespaces]
  (let [head {"Content-type" "application/json"}
        seq* #(or (try (seq %) (catch Throwable e)) [%])
        vars (->> namespaces (map seq*) (mapcat #(apply select-vars %)) set)]
    (fn [req]
      (if-not (= :post (:request-method req))
        (handler req)
        (binding [*print-meta* true
                  *request*    (atom req)
                  *session*    (atom (:session req))
                  r/*validate-only* (get-in req [:headers "x-castra-validate-only"])]
          (let [f #(do (csrf!) (do-rpc vars (decode-tunnel %)))
                d (try (encode-tunnel req (f req)) (catch Throwable e e))
                x (when (instance? Throwable d) (encode-tunnel req (ex->clj d)))]
            {:status (if x 500 200), :headers head, :body (or x d), :session @*session*}))))))

;; AJAX Crawling Middleware ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private js
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

(defn- tmp-file [ext content]
  (.getPath (doto (File/createTempFile "tmp-" ext) (spit content))))

(defn- scrape-phantomjs []
  (let [js-path (tmp-file js)]
    (fn [url]
      (let [{:keys [exit err out]} (sh/sh "phantomjs" js-path url)]
        (or (and (zero? exit) out)
          (throw (Exception. (format "phantomjs: %s (%d)" err exit))))))))

(defn wrap-escaped-fragment
  "Middleware to detect Google's `_escaped_fragment_` AJAX crawling requests [1]
  and serve a PhantomJS rendered version of the page. The `scrape` parameter
  must be a function that takes a URL and returns the HTML body of the rendered
  page (or throws an exception). See `scrape-phantomjs` above.

  NB: This middleware needs to be wrapped in middleware that will provide the
      :params request map key. A suitable candidate for this could be the ring
      #'ring.middleware.params/wrap-params middleware, for instance.

  [1]: https://developers.google.com/webmasters/ajax-crawling/docs/specification"
  [handler scrape]
  (fn [{{frag "_escaped_fragment_"} :params
        {:strs [host]}              :headers
        :keys [scheme uri request-method] :as req}]
    (let [{:keys [status] :as resp} (handler req)
          err   {:status 500 :body "Server Error"}
          url   (str (name scheme) "://" host uri "#!" frag)
          resp? (not (and frag (= [200 :get] [status request-method])))]
      (if resp? resp (try (assoc resp :body (scrape url)) (catch Throwable _ err))))))
