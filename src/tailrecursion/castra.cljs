;; Copyright (c) Alan Dipert and Micha Niskin. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns tailrecursion.castra
  (:require
    [tailrecursion.cljson :refer [cljson->clj clj->cljson]]))

(def ^:dynamic *url* nil)
(def csrf (atom ""))

(defrecord CastraEx [type isa message data cause trace status severity])

(def ex?  #(instance? CastraEx %))

(let [dfl (CastraEx. nil #{} "Server error." nil [] "" 500 :error)]
  (defn make-ex [m]
    (if (ex? m) m (merge dfl m))))

(defn ex-server [& [cause]]
  (let [ex (make-ex {:type    :tailrecursion.castra/exception
                     :message "The server is temporarily unavailable."})]
    (if cause (assoc ex :cause [cause]) ex)))

(defn jsex->ex [e] (ex-server (.-message e)))

(defn xhr! [xhr]
  (let [t (.-responseText xhr)
        c (.getResponseHeader xhr "X-CSRF-Token")]
    (when c (reset! csrf c))
    (try (cljson->clj t) (catch js/Error e (jsex->ex e)))))

(defn ajax [async? url expr out err fin fails]
  (let [csrf-kw   :tailrecursion.castra/csrf
        expr      (if (string? expr) expr (clj->cljson expr))
        retry!    #(ajax async? url expr out err fin (inc fails))
        handle-ex #(if (and (< fails 2) (= 403 (.-status %3))) (retry!) (%1 %2))
        wrap-out  (fn [_ _ x] (let [d (xhr! x)] ((if (ex? d) err out) d)))
        wrap-err  (fn [x _ _] (let [d (xhr! x)] (handle-ex err (make-ex d) x)))
        settings  {"async"        async?
                   "complete"     fin
                   "contentType"  "application/json"
                   "data"         expr
                   "dataType"     "text"
                   "error"        wrap-err
                   "headers"      {"X-CSRF-Token" @csrf
                                   "X-Tunnel"     "cljson"
                                   "Accept"       "application/json"}
                   "processData"  false
                   "success"      wrap-out
                   "type"         "POST"
                   "url"          url}]
    (-> js/jQuery (.ajax (clj->js settings)))))

(defn remote [async? url expr & [out err fin]]
  (let [wrap #(or % (constantly true))]
    (ajax async? url expr (wrap out) (wrap err) (wrap fin) 0)))

(def async (partial remote true))
(def sync  (partial remote false))

(defn safe-pop [x] (or (try (pop x) (catch js/Error e)) x))

(defn mkremote [endpoint state error loading]
  (let [url (or *url* (.. js/window -location -href))]
    (fn [& args]
      (swap! loading conj ::xhr)
      (async
        url
        `[~endpoint ~@args]
        (fn [x]
          (reset! error nil)
          (reset! state x))
        (fn [x] (reset! error x))
        (fn [_ _] (swap! loading safe-pop))))))
