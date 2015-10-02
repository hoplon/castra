;; Copyright (c) Alan Dipert and Micha Niskin. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns castra.core
  (:require [cognitect.transit :as t]))

;; helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- safe-pop
  [x]
  (or (try (pop x) (catch js/Error e)) x))

(defn- assoc-when
  [m k v]
  (if-not v m (assoc m k v)))

;; public api ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *validate-only*
  "Only validate request parameters, don't actually do it?"
  nil)

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
      (doto (ex-info message (or data {}) cause)
        (aset "serverStack" stack)
        (aset "status" status)))))

(defn- xhr-resp-headers
  [xhr headers]
  (reduce #(if-let [x (.getResponseHeader xhr %2)] (assoc %1 %2 x) %1) {} headers))

(defn ajax-fn
  "Ajax request implementation using the standard jQuery ajax machinery."
  [{:keys [url timeout credentials headers body]}]
  (let [prom (.Deferred js/jQuery)
        opts (-> {"async"       true
                  "contentType" "application/json"
                  "data"        body
                  "dataType"    "text"
                  "headers"     headers
                  "processData" false
                  "type"        "POST"
                  "url"         url
                  "timeout"     timeout}
                 (assoc-when "xhrFields" (assoc-when nil "withCredentials" credentials)))
        resp (fn [x]
               {:status      (.-status x)
                :status-text (.-statusText x)
                :body        (.-responseText x)
                :headers     (xhr-resp-headers x ["X-Castra-Tunnel" "X-Castra-Session"])})]
    (-> (.ajax js/jQuery (clj->js opts))
        (.done (fn [_ _ x] (.resolve prom (resp x))))
        (.fail (fn [x _ _] (.reject  prom (resp x)))))
    prom))

(def ^:private storage-key (str ::session))

(defn- get-session [ ] (.getItem js/localStorage storage-key))
(defn- set-session [x] (if (= x "DELETE")
                         (.removeItem js/localStorage storage-key)
                         (when x (.setItem js/localStorage storage-key x))))

(defn- ajax
  [{:keys [ajax-fn clj->json json->clj on-error] :as opts} expr]
  (let [prom    (.Deferred js/jQuery)
        headers (-> {"X-Castra-Csrf"          "true"
                     "X-Castra-Tunnel"        "transit"
                     "X-Castra-Validate-Only" (str (boolean *validate-only*))
                     "Accept"                 "application/json"}
                    (assoc-when "X-Castra-Session" (get-session)))
        body    (if (string? expr) expr (clj->json expr))
        wrap-ex #(make-ex {:message "Server Error" :cause %})
        ajax-ex #(wrap-ex (make-ex {:status %1 :message %2}))
        prom'   (ajax-fn (merge opts {:headers headers :body body}))
        resp    #(-> (json->clj %) (try (catch js/Error e {:error (wrap-ex e)})))]
    (-> prom'
        (.done (fn [{:keys [headers body]}]
                 (set-session (get headers "X-Castra-Session"))
                 (let [{:keys [ok error]} (resp body)]
                   (or (and (not error) (.resolve prom ok))
                       (.reject prom (doto (make-ex error) on-error))))))
        (.fail (fn [{:keys [headers body status status-text]}]
                 (.reject prom (doto (ajax-ex status status-text) on-error)))))
    (doto prom (aset "xhr" prom'))))

(defn with-default-opts
  [& [opts]]
  (->> opts (merge {:timeout     0
                    :credentials true
                    :on-error    identity
                    :ajax-fn     ajax-fn
                    :json->clj   (partial t/read (t/reader :json))
                    :clj->json   (partial t/write (t/writer :json))
                    :url         (.. js/window -location -href)})))

(defn mkremote
  "Given state error and loading input cells, returns an RPC function. The
  optional :url keyword argument can be used to specify the URL to which the
  POST requests will be made."
  [endpoint state error loading & [opts]]
  (fn [& args]
    (let [live?  (not *validate-only*)
          prom   (.Deferred js/jQuery)
          unload #(vec (remove (partial = prom) %))]
      (when live? (swap! loading (fnil conj []) prom))
      (let [prom' (-> (ajax (with-default-opts opts) `[~endpoint ~@args])
                      (.done   #(do (when live?
                                      (reset! error nil)
                                      (reset! state %))
                                    (.resolve prom %)))
                      (.fail   #(do (when live? (reset! error %))
                                    (.reject prom %)))
                      (.always #(when live? (swap! loading unload))))]
        (doto prom (aset "xhr" (aget prom' "xhr")))))))
