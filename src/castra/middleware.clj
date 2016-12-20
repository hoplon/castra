(ns castra.middleware
  (:require
    [clojure.java.shell             :as sh]
    [cognitect.transit              :as t]
    [ring.middleware.session.cookie :as c]
    [clojure.string                 :as string]
    [ring.util.request              :as q :refer [body-string]]
    [clojure.set                    :as s :refer [intersection difference]]
    [castra.core                    :as r :refer [*pre* *request* *session* *validate-only*]]
    [clojure.stacktrace             :as u :refer [print-cause-trace print-stack-trace]])
  (:import
    [java.io File]
    [java.util.regex Pattern]
    [java.io ByteArrayInputStream ByteArrayOutputStream]))

;; hat tip to io.aviso/pretty
(def ^:const ^:private ansi-pattern (Pattern/compile "\\e\\[.*?m"))
(defn ^String strip-ansi
  "Removes ANSI codes from a string, returning just the raw text."
  [string]
  (string/replace string ansi-pattern ""))

(defn- ex->clj [e]
  (->> (strip-ansi
         (with-out-str
           (try (print-cause-trace e)
                (catch Throwable x
                  (try (print-stack-trace e)
                       (catch Throwable x
                         (printf "No stack trace: %s" (.getMessage x))))))))
       (assoc (ex-data e) :stack)))

(defn- csrf! []
  (when-not (get-in *request* [:headers "x-castra-csrf"])
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

(def clj->json
  (atom #(let [out (ByteArrayOutputStream. 4096)]
           (t/write (t/writer out :json) %2)
           (.toString out))))

(def json->clj
  (atom #(-> (ByteArrayInputStream. (.getBytes %2)) (t/reader :json) t/read)))

(defn expression
  [body-keys req]
  (or (some req body-keys) (@json->clj req (body-string req))))

(defn response
  [body-keys req data]
  (if (some req body-keys) data (@clj->json req data)))

(defn headers
  [{:keys [body] :as req} head extra]
  (merge head (when-not (coll? body) extra)))

(def default-timeout (* 1000 60 60 24))

(defn- castra-req?
  [req]
  (and (get-in req [:headers "x-castra-csrf"])
       (= :post (:request-method req))))

(defn wrap-castra-session [handler ^String key & [{:keys [timeout]}]]
  (let [key     (.getBytes key)
        seal    (var-get #'ring.middleware.session.cookie/seal)
        unseal  (var-get #'ring.middleware.session.cookie/unseal)
        encrypt #(try (seal key %) (catch Throwable _))
        decrypt #(try (unseal key %) (catch Throwable _))]
    (assert (and (= (type (byte-array 0)) (type key))
                 (= (count key) 16))
            "the secret key must be 16 bytes")
    (fn [req]
      (if-not (castra-req? req)
        (handler req)
        (let [now    (System/currentTimeMillis)
              sess?  (contains? (:headers req) "x-castra-session")
              raw    (get-in req [:headers "x-castra-session"])
              data   (when raw (decrypt raw))
              expire (+ (or (:time data) now) (or timeout default-timeout))
              sess   (:data (when (and data (< now expire)) data))
              req'   (if-not sess? req (assoc req :session sess))
              resp   (handler req')
              data'  (if (and sess? (not (:session resp)))
                       "DELETE"
                       (when-let [s (:session resp)]
                         (encrypt {:time now :data s})))]
          (if-not data'
            resp
            (-> (dissoc resp :session)
                (assoc-in [:headers "X-Castra-Session"] data'))))))))

(defn wrap-castra [handler & [opts & more :as namespaces]]
  (let [{:keys [body-keys state-fn] :as opts} (when (map? opts) opts)
        nses (if opts more namespaces)
        head {"X-Castra-Tunnel" "transit"}
        seq* #(or (try (seq %) (catch Throwable e)) [%])
        vars (fn [] (->> nses (map seq*) (mapcat #(apply select-vars %)) set))]
    (fn [req]
      (if-not (castra-req? req)
        (handler req)
        (binding [*print-meta*    true
                  *pre*           true
                  *request*       req
                  *session*       (atom (:session req))
                  *validate-only* (= "true" (get-in req [:headers "x-castra-validate-only"]))]
          (let [h (headers req head {"Content-Type" "application/json"})
                f #(do (csrf!) (do-rpc (vars) (expression body-keys req)))
                d (try (response body-keys req {:result (f) :state (when state-fn (state-fn))})
                       (catch Throwable e
                         (response body-keys req {:error (ex->clj e)})))]
            {:status 200, :headers h, :body d, :session @*session*}))))))

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
