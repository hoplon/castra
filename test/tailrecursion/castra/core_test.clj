(ns tailrecursion.castra.core-test
  (:require [clojure.test :refer :all]
            [tailrecursion.castra :as c]
            [tailrecursion.castra :refer (defrpc)]
            :reload-all))

(deftest define-rpc-fn
  (testing "defn is an alias for defrpc"
    (is (= (c/defn foo [x]
             (+ 1 1))
           (defrpc foo [x]
             (+ 1 1)))))
  (testing "defn is deprecated in 1.0.2"
    (is (= (:deprecated (meta (var c/defn))) "1.0.2"))))
