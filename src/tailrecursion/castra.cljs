;; Copyright (c) Alan Dipert and Micha Niskin. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns tailrecursion.castra
  (:require [cognitect.transit :as t]))

(def ^:dynamic *validate-only* nil)
(def ^:dynamic *ajax-timeout*  (atom nil))

(def json->clj (atom (partial t/read (t/reader :json))))
(def clj->json (atom (partial t/write (t/writer :json))))

(defn- safe-pop
  [x]
  (or (try (pop x) (catch js/Error e)) x))

(defn ex? [x] (instance? ExceptionInfo x))

(defn make-ex
  [ex]
  (if (ex? ex)
    ex
    (let [{:keys [status message data stack cause]} ex]
      (doto (ex-info message data cause)
        (aset "serverStack" stack)
        (aset "status" status)))))

(defn json->clj*
  [x]
  (try (@json->clj x)
       (catch js/Error e
         (make-ex {:message "Server Error" :cause e}))))

(def ajax-impl
  (atom (fn [url data headers done fail always]
          (-> js/jQuery
              (.ajax (clj->js (merge {"async"       true
                                      "contentType" "application/json"
                                      "data"        data
                                      "dataType"    "text"
                                      "headers"     headers
                                      "processData" false
                                      "type"        "POST"
                                      "url"         url}
                                     (when-let [t @*ajax-timeout*] {"timeout" t}))))
              (.done (fn [_ _ x] (done (json->clj* (aget x "responseText")))))
              (.fail (fn [x t r] (let [status  (.-status x)
                                       head    (aget x "getResponseHeader")
                                       tunnel? (.call head x "X-Castra-Tunnel")
                                       body    (if-not tunnel?
                                                 {:message r :status status}
                                                 (json->clj* (aget x "responseText")))]
                                   (fail (make-ex body)))))
              (.always (fn [_ _] (always)))))))

(defn- ajax
  [url expr done fail always]
  (let [headers {"X-Castra-Csrf"          "true"
                 "X-Castra-Tunnel"        "transit"
                 "X-Castra-Validate-Only" (str (boolean *validate-only*))
                 "Accept"                 "application/json"}
        expr    (if (string? expr) expr (@clj->json expr))
        done    #((if (ex? %) fail done) %)]
    (@ajax-impl url expr headers done fail always)))

(defn mkremote
  [endpoint state error loading & {:keys [url]}]
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
