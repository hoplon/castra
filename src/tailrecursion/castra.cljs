;; Copyright (c) Alan Dipert and Micha Niskin. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns tailrecursion.castra
  (:require [cognitect.transit :as t]))

(def ^:dynamic *validate-only*
  "Only validate request parameters, don't actually do it?"
  nil)

(def ^:dynamic *ajax-timeout*
  "The timeout for XHR requests in ms (0 means no timeout)."
  (atom 0))

(def ^:dynamic *with-credentials*
  "Send cookies when making CORS requests?"
  (atom true))

(def json->clj
  "Atom containing function to convert wire format JSON to ClojureScript data."
  (atom (partial t/read (t/reader :json))))

(def clj->json
  "Atom containing function to convert ClojureScript data to wire format JSON."
  (atom (partial t/write (t/writer :json))))

(defn- safe-pop
  [x]
  (or (try (pop x) (catch js/Error e)) x))

(defn ex?
  "Returns true if x is an ExceptionInfo."
  [x]
  (instance? ExceptionInfo x))

(defn make-ex
  "Given either an existing exception or a map, returns an ExceptionInfo
  object with the special status and serverStack properties set. If ex is
  an exception already then ex itself is returned."
  [ex]
  (if (ex? ex)
    ex
    (let [{:keys [status message data stack cause]} ex]
      (doto (ex-info message data cause)
        (aset "serverStack" stack)
        (aset "status" status)))))

(defn json->clj*
  "Converts wire format JSON to CLJS data (see json->clj above). If an
  exception is thrown during conversion an ExceptionInfo object is returned."
  [x]
  (try (@json->clj x)
       (catch js/Error e
         (make-ex {:message "Server Error" :cause e}))))

(def ajax-fn
  "Atom containing the ajax request implementation. The default is to use the
  standard jQuery ajax machinery."
  (atom (fn [url data headers done fail always]
          (let [opts {"async"       true
                      "contentType" "application/json"
                      "data"        data
                      "dataType"    "text"
                      "headers"     headers
                      "processData" false
                      "type"        "POST"
                      "url"         url
                      "timeout"     *ajax-timeout*
                      "xhrFields"   {"withCredentials" @*with-credentials*}}]
            (-> js/jQuery
                (.ajax (clj->js opts))
                (.done (fn [_ _ x] (done (json->clj* (aget x "responseText")))))
                (.fail (fn [x t r] (let [status  (.-status x)
                                         head    (aget x "getResponseHeader")
                                         tunnel? (.call head x "X-Castra-Tunnel")
                                         body    (if-not tunnel?
                                                   {:message r :status status}
                                                   (json->clj* (aget x "responseText")))]
                                     (fail (make-ex body)))))
                (.always (fn [_ _] (always))))))))

(defn- ajax
  [url expr done fail always]
  (let [headers {"X-Castra-Csrf"          "true"
                 "X-Castra-Tunnel"        "transit"
                 "X-Castra-Validate-Only" (str (boolean *validate-only*))
                 "Accept"                 "application/json"}
        expr    (if (string? expr) expr (@clj->json expr))
        done    #((if (ex? %) fail done) %)]
    (ajax-fn url expr headers done fail always)))

(defn default-opts
  [opts]
  (->> opts (merge {:ajax-fn     ajax-fn
                    :json->clj   json->clj
                    :clj->json   clj->json
                    :timeout     timeout
                    :credentials credentials
                    :url         (.. js/window -location -href)})))

(defn mkremote
  "Given state error and loading input cells, returns an RPC function. The
  optional :url keyword argument can be used to specify the URL to which the
  POST requests will be made."
  [endpoint state error loading & [opts]]
  (let [url (or url (.. js/window -location -href))]
    (fn [& args]
      (let [p (.Deferred js/jQuery)]
        (swap! loading (fnil conj []) p)
        (let [xhr (ajax
                    url
                    `[~endpoint ~@args]
                    #(do (reset! error nil) (reset! state %) (.resolve p %))
                    #(do (reset! error %) (.reject p %))
                    #(swap! loading (fn [x] (vec (remove (partial = p) x)))))]
          (doto p (aset "xhr" xhr)))))))
