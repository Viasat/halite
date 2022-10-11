;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.propagate.test-prop-strings
  (:require [jibe.halite.propagate.prop-strings :as prop-strings]
            [jibe.halite.transpile.ssa :as ssa]
            [loom.graph :as loom-graph]
            [schema.test])
  (:import [org.chocosolver.solver.exception ContradictionException])
  (:use clojure.test))

(use-fixtures :once schema.test/validate-schemas)

(def simple-example
  '{:spec-vars {:s1 "String" :s2 "String" :s3 "String"} :refines-to {}
    :constraints [["c1" (and (not= s1 "foo")
                             (= s1 s2)
                             (or (= s2 "foo") (= s2 "bar"))
                             (not= s1 s3))]]})

(def compute-string-comparison-graph #'prop-strings/compute-string-comparison-graph)

(deftest test-compute-string-comparison-graph
  (let [spec (ssa/spec-to-ssa {} simple-example)
        ssa-graph (:ssa-graph spec)
        expected (->> '[[s1 "foo"]
                        [s1 s2]
                        [s2 "foo"]
                        [s2 "bar"]
                        [s1 s3]]
                      (mapv #(map (partial ssa/find-form ssa-graph) %))
                      (apply loom-graph/graph))
        actual (compute-string-comparison-graph spec)]
    (is (= expected actual))))

(def label-scg #'prop-strings/label-scg)
(def lower-spec #'prop-strings/lower-spec)

(deftest test-lower-spec
  (let [spec (ssa/spec-to-ssa {} simple-example)
        scg (label-scg (compute-string-comparison-graph spec))
        [lowered alts] (lower-spec spec scg)]
    (is (= ['{:spec-vars {:$10=$5 "Boolean", :$2=$1 "Boolean", :$5=$1 "Boolean", :$5=$2 "Boolean", :$15=$1 "Boolean"
                          :$s1$alts [:Maybe "Integer"] :$s2$alts [:Maybe "Integer"]}
              :refines-to {}
              :constraints [["$all"
                             (let [v1 (not $2=$1)]
                               (and
                                ;; c1
                                (and v1 $5=$1 (or $5=$2 $10=$5) (not $15=$1))
                                ;; mutual exclusion for s1
                                (if-value $s1$alts
                                  (= (= 0 $s1$alts) $2=$1)
                                  v1)
                                ;; mutual exclusion for s2
                                (if-value $s2$alts
                                  (and
                                   (= (= 0 $s2$alts) $10=$5)
                                   (= (= 1 $s2$alts) $5=$2))
                                  (and (not $10=$5) (not $5=$2)))))]]}
            {:$s1$alts {:Unset :String, 0 "foo"}
             :$s2$alts {:Unset :String, 0 "bar", 1 "foo"}}]
           [(ssa/spec-from-ssa lowered)
            alts]))))

(def disjoint-string-bounds? #'prop-strings/disjoint-string-bounds?)

(deftest test-disjoint-string-bounds?
  (are [result a b]
      (= result (disjoint-string-bounds? a b) (disjoint-string-bounds? b a))

    false :String :String
    true  "foo" "bar"
    false {:$in #{"foo" "bar"}} {:$in #{"bar" "baz"}}
    true {:$in #{"foo" "bar"}} {:$in #{"baz" "lerman"}}
    false :String "foo"
    false :String {:$in #{"foo" "bar"}}))

(def lower-spec-bound #'prop-strings/lower-spec-bound)

(deftest test-lower-spec-bound
  (let [spec (ssa/spec-to-ssa {} simple-example)
        scg (label-scg (compute-string-comparison-graph spec))
        [spec' alts] (lower-spec spec scg)
        base-bounds {:$s1$alts {:$in #{0 :Unset}} :$s2$alts {:$in #{0 1 :Unset}}}]
    (are [in out]
        (= (merge base-bounds out) (lower-spec-bound scg spec alts in))

      {} {}
      {:s1 :String} {}
      {:s1 "foo"} {:$2=$1 true}
      {:s1 {:$in #{"foo"}}} {:$2=$1 true}
      {:s1 "baz"} {:$2=$1 false}
      {:s2 "baz"} {:$5=$2 false :$10=$5 false}
      {:s2 {:$in #{"foo" "baz"}}} {:$10=$5 false}
      )))

(def raise-spec-bound #'prop-strings/raise-spec-bound)

(deftest test-raise-spec-bound
  (let [spec (ssa/spec-to-ssa {} simple-example)
        scg (-> spec compute-string-comparison-graph label-scg)
        [spec' alts] (lower-spec spec scg)]
    (are [in out]
        (= out (raise-spec-bound in scg spec alts {}))

      {:$10=$5 {:$in #{true false}}, :$15=$1 false, :$5=$1 true, :$5=$2 {:$in #{true false}}, :$2=$1 false
       :$s1$alts :Unset, :$s2$alts {:$in #{0 1}}}
      {:s1 :String :s2 {:$in #{"foo" "bar"}} :s3 :String}

      ;; {:$10=$5 true, :$2=$1 false, :$5=$1 true, :$5=$2 false}
      ;; {:s1 "bar" :s2 "bar"}
      )))

(deftest test-propagate-simple-example
  (are [in out]
      (= out (prop-strings/propagate simple-example in))

    {} {:s1 :String :s2 {:$in #{"foo" "bar"}} :s3 :String}
    {:s2 {:$in #{"bar" "baz"}}} {:s1 "bar" :s2 "bar" :s3 :String}))

(def mixed-constraints-example
  '{:spec-vars {:p "Boolean" :n "Integer" :s1 "String" :s2 "String"}
    :constraints [["c1" (if p
                          (= s1 "foo")
                          (< n 5))]
                  ["c2" (= (= s1 s2) (> n 5))]
                  ["c3" (or (= "foo" s2)
                            (= "bar" s2)
                            (= "baz" s2))]
                  ["c4" (and (<= 0 n) (<= n 10))]]
    :refines-to {}})

(deftest test-propagate-mixed-constraints-example
  (schema.core/without-fn-validation
   (are [in out]
       (= out (try (prop-strings/propagate mixed-constraints-example in)
                   (catch ContradictionException ex
                     :contradiction)))

     {}
     {:n {:$in [0 10]}, :p {:$in #{false true}}, :s1 :String, :s2 {:$in #{"bar" "baz" "foo"}}}

     {:p true}
     {:n {:$in [0 10]}, :p true, :s1 "foo", :s2 {:$in #{"bar" "baz" "foo"}}}

     {:p true :s1 "bar"}
     :contradiction

     {:p true :s2 "bar"}
     {:n {:$in [0 10]}, :p true, :s1 "foo", :s2 "bar"}

     {:p true :s2 "foo"}
     {:n {:$in [0 10]}, :p true, :s1 "foo", :s2 "foo"}

     {:s1 "foo" :s2 "bar"}
     {:n {:$in [0 5]}, :p {:$in #{false true}}, :s1 "foo", :s2 "bar"})))
