;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-refinements
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.op-refinements :as op-refinements]
            [schema.test])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-basic
  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :s {:$expr '(get {:$type :ws/B$v1
                                                     :s (+ x y)}
                                                    :s)}}}}
         (op-refinements/refinements-op {:ws/B$v1 {:fields {:s :Integer}}
                                         :ws/A$v1 {:fields {:x :Integer
                                                            :y :Integer}
                                                   :refines-to {:ws/B$v1
                                                                {:name "specId"
                                                                 :expr '{:$type :ws/B$v1
                                                                         :s (+ x y)}}}}}
                                        {:$instance-of :ws/A$v1
                                         :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1}}})))

  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                   :s {:$expr '(get {:$type :ws/B$v1
                                                     :s (+ x y)}
                                                    :s)}
                                   :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                            :c {:$expr '(get {:$type :ws/C$v1
                                                                              :c (+ 1 s)
                                                                              :k (+ 2 s)}
                                                                             :c)}
                                                            :k {:$expr '(get {:$type :ws/C$v1
                                                                              :c (+ 1 s)
                                                                              :k (+ 2 s)}
                                                                             :k)}}}}}}
         (op-refinements/refinements-op {:ws/C$v1 {:fields {:c :Integer
                                                            :k :Integer}}
                                         :ws/B$v1 {:fields {:s :Integer}
                                                   :refines-to {:ws/C$v1
                                                                {:name "specId"
                                                                 :expr '{:$type :ws/C$v1
                                                                         :c (+ 1 s)
                                                                         :k (+ 2 s)}}}}
                                         :ws/A$v1 {:fields {:x :Integer
                                                            :y :Integer}
                                                   :refines-to {:ws/B$v1
                                                                {:name "specId"
                                                                 :expr '{:$type :ws/B$v1
                                                                         :s (+ x y)}}}}}
                                        {:$instance-of :ws/A$v1
                                         :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                                                  :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1}}}}}))))

;; (run-tests)
