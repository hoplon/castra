;; Copyright (c) Alan Dipert and Micha Niskin. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns tailrecursion.castra
  (:require [cognitect.transit :as t]))

(def ^:dynamic *url* nil)
(def ^:dynamic *validate-only* nil)

(defn make-ex
  [{:keys [message data stack]}]
  (doto (ex-info message data) (aset "stack" stack)))

(defn ex? [x] (instance? ExceptionInfo ex))

(defn jq-ajax [url data headers done fail always]
  (.. js/jQuery
    (ajax (clj->js {"async"       true
                    "contentType" "application/json"
                    "data"        data
                    "dataType"    "text"
                    "headers"     headers
                    "processData" false
                    "type"        "POST"
                    "url"         url}))
    (done (fn [_ _ x] (done (aget x "responseText"))))
    (fail (fn [x _ _] (when (not= 0 (.-status x)) (fail (aget x "responseText")))))
    (always (fn [_ _] (always)))))

(def ajax-impl (atom jq-ajax))
(def json->clj (atom (partial t/read (t/reader :json))))
(def clj->json (atom (partial t/write (t/writer :json))))

(defn ajax [url expr done fail always]
  (let [headers   {"X-Castra-Csrf"          "true"
                   "X-Castra-Tunnel"        "transit"
                   "X-Castra-Validate-Only" (str (boolean *validate-only*))
                   "Accept"                 "application/json"}
        unserial  #(try (@json->clj %)
                        (catch js/Error e (ex-info "Server error." {} e)))
        expr      (if (string? expr) expr (@clj->json expr))
        wrap-fail #(fail (make-ex (unserial %)))
        wrap-done #(let [d (unserial %)] ((if (ex? d) fail done) d))]
    (@ajax-impl url expr headers wrap-done wrap-fail always)))

(defn remote [url expr & [done fail always]]
  (let [wrap #(or % (constantly true))]
    (ajax url expr (wrap done) (wrap fail) (wrap always))))

(defn safe-pop [x] (or (try (pop x) (catch js/Error e)) x))

(defn mkremote [endpoint state error loading & {:keys [url]}]
  (let [url (or url *url* (.. js/window -location -href))]
    (fn [& args]
      (let [p (.Deferred js/jQuery)]
        (swap! loading (fnil conj []) p)
        (let [xhr (remote
                    url
                    `[~endpoint ~@args]
                    #(do (reset! error nil) (reset! state %) (.resolve p %))
                    #(do (reset! error %) (.reject p %))
                    #(swap! loading (fn [x] (vec (remove (partial = p) x)))))]
          (doto p (aset "xhr" xhr)))))))
