(ns tailrecursion.castra
  (:refer-clojure :exclude [isa?])
  (:require
    [cljs.reader :refer [read-string]]))

(def ^:dynamic *url* "")
(def csrf (atom ""))

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

(defn xhr! [xhr]
  (let [t (.-responseText xhr)
        c (.getResponseHeader xhr "X-Csrf")]
    (if c (reset! csrf c))
    (try (read-string t) (catch js/Error e (jsex->ex e)))))

(defn ajax [async? edn out err fin fails]
  (let [csrf-kw   :tailrecursion.castra/csrf
        retry!    #(ajax async? edn out err fin (inc fails))
        handle-ex #(if (and (< fails 2) (isa? %2 csrf-kw)) (retry!) (%1 %2))
        wrap-out  (fn [_ _ x] (let [d (xhr! x)] ((if (ex? d) err out) d)))
        wrap-err  (fn [x _ _] (let [d (xhr! x)] (handle-ex err (make-ex d))))
        settings  {"async"        async?
                   "complete"     fin
                   "contentType"  "application/edn"
                   "data"         edn
                   "dataType"     "text"
                   "error"        wrap-err 
                   "headers"      {"X-Csrf" @csrf
                                   "Accept" "application/edn"}
                   "processData"  false
                   "success"      wrap-out
                   "type"         "POST"
                   "url"          *url*}]
    (-> js/jQuery (.ajax (clj->js settings)))))

(defn remote [async? edn & [out err fin]]
  (let [wrap #(or % (constantly true))]
    (ajax async? edn (wrap out) (wrap err) (wrap fin) 0)))

(def async (partial remote true))
(def sync  (partial remote false))
