;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.test-op-refinement-constraints
  (:require [clojure.test :refer :all]
            [com.viasat.halite.op-refinement-constraints :as op-refinement-constraints]
            [schema.core :as s]
            [schema.test]))

(set! *warn-on-reflection* true)

(def fixtures (join-fixtures [schema.test/validate-schemas]))

(use-fixtures :each fixtures)

(deftest test-op-refinement-constraints
  (is (= {:$instance-of :ws/A$v1
          :f {:$value? true, :$primitive-type :Boolean}
          :a {:$value? true, :$primitive-type :Integer}
          :b {:$value? true, :$primitive-type :Integer}
          :c {:$value? true, :$primitive-type :Integer}
          :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1}}
          :$constraints {"r/ws/X$v1/c1" '(let [x0 (if f
                                                    {:$type :ws/X$v1, :x a, :y b}
                                                    {:$type :ws/X$v1, :x b, :y c})]
                                           true)}}
         (op-refinement-constraints/refinement-constraints-op {:ws/X$v1 {:fields {:x :Integer
                                                                                  :y :Integer}
                                                                         :constraints [["c1" '(= 10 (+ x y))]]}
                                                               :ws/A$v1 {:fields {:f :Boolean
                                                                                  :a :Integer
                                                                                  :b :Integer
                                                                                  :c :Integer}
                                                                         :refines-to {:ws/X$v1
                                                                                      {:name "r1"
                                                                                       :expr '(if f
                                                                                                {:$type :ws/X$v1
                                                                                                 :x a
                                                                                                 :y b}
                                                                                                {:$type :ws/X$v1
                                                                                                 :x b
                                                                                                 :y c})}}}}

                                                              {:$instance-of :ws/A$v1
                                                               :f {:$value? true, :$primitive-type :Boolean}
                                                               :a {:$value? true, :$primitive-type :Integer}
                                                               :b {:$value? true, :$primitive-type :Integer}
                                                               :c {:$value? true, :$primitive-type :Integer}
                                                               :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1}}})))

  (is (= {:$instance-of :ws/A$v1
          :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                   :$refinements {:ws/P$v1 {:$instance-of :ws/P$v1}}}}
          :$constraints {"r/ws/X$v1/c1" '(let [x0 (if f
                                                    {:$type :ws/X$v1, :x a, :y b}
                                                    {:$type :ws/X$v1, :x b, :y c})]
                                           true)
                         "r/ws/P$v1/c2" '(let [x (get (if f {:$type :ws/X$v1, :x a, :y b} {:$type :ws/X$v1, :x b, :y c}) :x)
                                               y (get (if f {:$type :ws/X$v1, :x a, :y b} {:$type :ws/X$v1, :x b, :y c}) :y)]
                                           (let [x0 {:$type :ws/P$v1, :p x, :q y}]
                                             true))}}
         (op-refinement-constraints/refinement-constraints-op {:ws/P$v1 {:fields {:p :Integer
                                                                                  :q :Integer}
                                                                         :constraints [["c2" '(= 2 (- p q))]]}
                                                               :ws/X$v1 {:fields {:x :Integer
                                                                                  :y :Integer}
                                                                         :constraints [["c1" '(= 10 (+ x y))]]
                                                                         :refines-to {:ws/P$v1
                                                                                      {:name "r2"
                                                                                       :expr '{:$type :ws/P$v1
                                                                                               :p x
                                                                                               :q y}}}}
                                                               :ws/A$v1 {:fields {:f :Boolean
                                                                                  :a :Integer
                                                                                  :b :Integer
                                                                                  :c :Integer}
                                                                         :refines-to {:ws/X$v1
                                                                                      {:name "r1"
                                                                                       :expr '(if f
                                                                                                {:$type :ws/X$v1
                                                                                                 :x a
                                                                                                 :y b}
                                                                                                {:$type :ws/X$v1
                                                                                                 :x b
                                                                                                 :y c})}}}}
                                                              {:$instance-of :ws/A$v1
                                                               :$refinements {:ws/X$v1 {:$instance-of :ws/X$v1
                                                                                        :$refinements {:ws/P$v1 {:$instance-of :ws/P$v1}}}}}))))

;; (run-tests)
