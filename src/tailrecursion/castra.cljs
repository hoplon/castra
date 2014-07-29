;; Copyright (c) Alan Dipert and Micha Niskin. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns tailrecursion.castra
  (:refer-clojure :exclude [isa?])
  (:require
    [tailrecursion.cljson :refer [cljson->clj clj->cljson]]))

(def ^:dynamic *url* nil)

(defrecord CastraEx [type isa message data cause trace status severity])

(def ex?  #(instance? CastraEx %))
(def isa? #(contains? (conj (:isa %1 #{}) (:type %1)) %2))

(let [dfl (CastraEx. nil #{} "Server error." nil [] "" 500 :error)]
  (defn make-ex [m]
    (if (ex? m) m (merge dfl m))))

(defn ex-server [& [cause]]
  (let [ex (make-ex {:type    :tailrecursion.castra/exception
                     :message "The server is temporarily unavailable."})]
    (if cause (assoc ex :cause [cause]) ex)))

(defn jsex->ex [e] (ex-server (.-message e)))

(defn jq-ajax [async? url data headers done fail always]
  (.. js/jQuery
    (ajax (clj->js {"async"       async?
                    "contentType" "application/json"
                    "data"        expr
                    "dataType"    "text"
                    "headers"     headers
                    "processData" false
                    "type"        "POST"
                    "url"         url}))
    (done (fn [_ _ x] (done x)))
    (fail (fn [x _ _] (fail x)))
    (always (fn [_ _] (always)))))

(defn ajax [async? url expr done fail always & {:keys [ajax-impl]}]
  (let [headers   {"X-Castra-Csrf"   "true"
                   "X-Castra-Tunnel" "cljson"
                   "Accept"          "application/json"}
        unserial  #(try (cljson->clj (.-responseText xhr))
                        (catch js/Error e (jsex->ex e)))
        expr      (if (string? expr) expr (clj->cljson expr))
        wrap-fail #(fail (make-ex (unserial %)))
        wrap-done #(let [d (unserial %)] ((if (ex? d) fail done) d))]
    ((or ajax-impl jq-ajax) async? url expr headers wrap-done wrap-fail always)))

(defn remote [async? url expr & [done fail always]]
  (let [wrap #(or % (constantly true))]
    (ajax async? url expr (wrap done) (wrap fail) (wrap always))))

(def async (partial remote true))
(def sync  (partial remote false))

(defn safe-pop [x] (or (try (pop x) (catch js/Error e)) x))

(defn mkremote [endpoint state error loading & [url ajax-impl]]
  (let [url (or url *url* (.. js/window -location -href))]
    (fn [& args]
      (swap! loading conj ::xhr)
      (async
        url
        `[~endpoint ~@args]
        #(do (reset! error nil) (reset! state %))
        #(reset! error %)
        #(swap! loading safe-pop)
        :ajax-impl ajax-impl))))
