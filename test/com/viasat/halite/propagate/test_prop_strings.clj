;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.test-prop-strings
  (:require [com.viasat.halite.propagate.prop-strings :as prop-strings]
            [com.viasat.halite.transpile.ssa :as ssa]
            [loom.graph :as loom-graph]
            [loom.label :as loom-label]
            [schema.core :as s]
            [schema.test])
  (:import [org.chocosolver.solver.exception ContradictionException])
  (:use clojure.test))

(use-fixtures :once schema.test/validate-schemas)

(def simple-example
  '{:spec-vars {:s1 "String" :s2 "String" :s3 "String"}
    :constraints [["c1" (and (not= s1 "foo")
                             (= s1 s2)
                             (or (= s2 "foo") (= s2 "bar"))
                             (not= s1 s3))]]})

(def compute-string-comparison-graph #'prop-strings/compute-string-comparison-graph)

(deftest test-compute-string-comparison-graph
  (let [spec (ssa/spec-to-ssa {} simple-example)
        expected '{s1 #{"foo" s2 s3}
                   s2 #{s1 "foo" "bar"}
                   s3 #{s1}
                   :Unset #{}}
        expected-edges (set (for [[a bs] expected, b bs] #{a b}))
        scg (compute-string-comparison-graph spec)]
    (is (= #{'s1 's2 's3 "foo" "bar" :Unset} (set (loom-graph/nodes scg))))
    (is (= expected-edges
           (set (map set (loom-graph/edges scg)))))

    ;; test node labels
    (are [node node-lbl]
         (= node-lbl (loom-label/label scg node))

      's1 {:alt-var '$s1 :alt-vals {"foo" 0}}
      's2 {:alt-var '$s2 :alt-vals {"bar" 0, "foo" 1}}
      's3 {:alt-var '$s3 :alt-vals {}})

    ;; test edge labels
    (are [a b edge-lbl]
         (= edge-lbl (loom-label/label scg a b))

      's1 "foo" '(if-value $s1 (= $s1 0) false)
      's1 's2 '$s1=s2)))

(def simplify-string-exprs #'prop-strings/simplify-string-exprs)

(deftest test-simplify-string-exprs
  ;; literal-literal string comparisons need to be evaluated
  (let [spec (->> '{:spec-vars {:p "Boolean"}
                    :constraints [["c" (= "foo" (if p "foo" "bar"))]]}
                  (ssa/spec-to-ssa {})
                  (simplify-string-exprs))]
    (is (= 'p
           (-> spec (ssa/spec-from-ssa) :constraints first second))))

  ;; multi-arity comparisons need to be expanded to binary comparisons
  (let [spec (->> '{:spec-vars {:s1 "String" :s2 [:Maybe "String"] :s3 [:Maybe "String"]}
                    :constraints [["c1" (= s1 "foo" s2)]
                                  ["c2" (not= s2 "bar" "baz" $no-value)]
                                  ["c3" (not= s1 s2 s3)]]}
                  (ssa/spec-to-ssa {})
                  (simplify-string-exprs))]
    (is (= '(and (and (= s1 "foo") (= "foo" s2))
                 (not (and false (= s2 "bar")))
                 (not (and (= s1 s2) (= s2 s3))))
           (-> spec (ssa/spec-from-ssa) :constraints first second)))))

(def lower-spec #'prop-strings/lower-spec)

(deftest test-lower-spec
  (let [spec (ssa/spec-to-ssa {} simple-example)
        scg (compute-string-comparison-graph spec)
        lowered (lower-spec spec scg)]
    (is (= '{:spec-vars {:$s1 [:Maybe "Integer"] :$s2 [:Maybe "Integer"] :$s3 [:Maybe "Integer"]
                         :$s1=s2 "Boolean", :$s1=s3 "Boolean"}
             :constraints [["$all"
                            (and
                             ;; (not= s1 "foo")
                             (not (if-value $s1 (= $s1 0) false))
                             $s1=s2
                             (or
                              ;; (= s2 "foo")
                              (if-value $s2 (= $s2 1) false)
                              ;; (= s2 "bar")
                              (if-value $s2 (= $s2 0) false))
                             (not $s1=s3))]]
             :refines-to {}}
           (ssa/spec-from-ssa lowered)))))

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
        scg (compute-string-comparison-graph spec)
        base-bounds {:$s1 {:$in #{0 :Unset}} :$s2 {:$in #{0 1 :Unset}} :$s3 :Unset}]
    (are [in out]
         (= (merge base-bounds out) (lower-spec-bound in scg))

      ;; default bounds
      {} {}
      {:s1 :String} {}

      ;; a var is a specific alternative
      {:s1 "foo"} {:$s1 0}
      {:s1 {:$in #{"foo"}}} {:$s1 0}
      {:s2 {:$in #{"foo" "bar"}}} {:$s2 {:$in #{0 1}}}

      ;; a var is not certain alternatives
      {:s2 {:$in #{"foo" "blerg"}}} {:$s2 {:$in #{:Unset 1}}}

      ;; a var is definitely not any alternative
      {:s1 "blerg"} {:$s1 :Unset}
      {:s1 {:$in #{"blerg" "blag"}}} {:$s1 :Unset}

      ;; two directly compared vars have disjoint bounds
      {:s1 "foo" :s2 "bar"} {:$s1 0 :$s2 0 :$s1=s2 false}
      {:s1 {:$in #{"foo" "blerg"}} :s2 {:$in #{"bar" "blag"}}} {:$s1 {:$in #{:Unset 0}} :$s2 {:$in #{:Unset 0}} :$s1=s2 false}

      ;; two directly compared vars definitely have the same value
      {:s1 "foo" :s2 "foo"} {:$s1 0 :$s2 1 :$s1=s2 true})))

(def raise-spec-bound #'prop-strings/raise-spec-bound)

(deftest test-raise-spec-bound
  (let [spec (ssa/spec-to-ssa {} simple-example)
        scg (compute-string-comparison-graph spec)]
    (are [in out]
         (= out (try (raise-spec-bound in scg {})
                     (catch ContradictionException ex
                       :contradiction)))

      ;; bounds derived directly from alts
      {:$s1 {:$in #{0 :Unset}}, :$s1=s2 true, :$s1=s3 false, :$s2 {:$in #{0 1}}, :$s3 :Unset}
      {:s1 :String :s2 {:$in #{"foo" "bar"}} :s3 :String}

      ;; bounds derived indirectly from symbol-symbol comparisons
      ;; when one bound has collapsed
      {:$s1 :Unset, :$s3 :Unset, :$s2 0, :$s1=s3 false, :$s1=s2 true}
      {:s1 "bar" :s2 "bar" :s3 :String}

      ;; contradiction derived indirectly from symbol-symbol comparisons
      {:$s1 0 :$s2 {:$in #{:Unset 0}} :$s3 :Unset :$s1=s3 false :$s1=s2 true}
      :contradiction

      ;; bounds derived indirectly from symbol-symbol comparisons
      ;; by subtracting disproven alternatives
      {:$s1 :Unset, :$s1=s2 true, :$s1=s3 false, :$s2 {:$in #{0 1}}, :$s3 :Unset}
      {:s1 :String :s2 "bar" :s3 :String})))

(deftest test-propagate-simple-example
  (let [spec (ssa/spec-to-ssa {} simple-example)]
    (are [in out]
         (= out (try (prop-strings/propagate spec in)
                     (catch ContradictionException ex
                       :contradiction)))

      {} {:s1 :String :s2 "bar" :s3 :String}
      {:s2 {:$in #{"bar" "baz"}}} {:s1 "bar" :s2 "bar" :s3 :String})))

(def mixed-constraints-example
  '{:spec-vars {:p "Boolean" :n "Integer" :s1 "String" :s2 "String"}
    :constraints [["c1" (if p
                          (= s1 "foo")
                          (< n 5))]
                  ["c2" (= (= s1 s2) (> n 5))]
                  ["c3" (or (= "foo" s2)
                            (= "bar" s2)
                            (= "baz" s2))]
                  ["c4" (and (<= 0 n) (<= n 10))]]})

(deftest test-propagate-mixed-constraints-example
  (let [spec (ssa/spec-to-ssa {} mixed-constraints-example)]
    (schema.core/without-fn-validation
     (are [in out]
          (= out (try (prop-strings/propagate spec in)
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
       {:n {:$in [0 5]}, :p {:$in #{false true}}, :s1 "foo", :s2 "bar"}

       {:s1 "blerg"}
       {:n {:$in [0 4]}, :p false, :s1 "blerg", :s2 {:$in #{"bar" "baz" "foo"}}}

       {:s1 "blerg" :n 2 :p false :s2 "baz"}
       {:s1 "blerg" :n 2 :p false :s2 "baz"}))))

(def simple-optional-string-var-example
  '{:spec-vars {:s1 [:Maybe "String"], :s2 "String"}
    :constraints [["c1" (= s2 (if-value s1 s1 "bar"))]
                  ["c2" (not= s2 "baz")]
                  ["c3" (or (= s1 "foo") (= $no-value s1))]]})

(comment
  "simplifies to"
  {:spec-vars {:s1 [:Maybe "String"], :s2 "String"},
   :constraints [["$all"
                  (and
                   (if ($value? s1)
                     (= s2 ($value! s1))
                     (= s2 "bar"))
                   (not= s2 "baz")
                   (or (= s1 "foo") (= $no-value s1)))]]})

(deftest test-compute-string-comparison-graph-with-optional-string-vars
  (let [spec (simplify-string-exprs (ssa/spec-to-ssa {} simple-optional-string-var-example))
        expected '{s1 #{"foo" s2 :Unset}
                   s2 #{s1 "bar" "baz"}}
        expected-edges (set (for [[a bs] expected, b bs] #{a b}))
        scg (compute-string-comparison-graph spec)]

    (is (= (set (apply concat expected-edges)) (set (loom-graph/nodes scg))))
    (is (= expected-edges (set (map set (loom-graph/edges scg)))))

    ;; test node labels
    (are [node node-lbl]
         (= node-lbl (loom-label/label scg node))

      's1 {:alt-var '$s1 :alt-vals {:Unset 0 "foo" 1}}
      's2 {:alt-var '$s2 :alt-vals {"bar" 0 "baz" 1}})

    ;; test edge labels
    (are [a b edge-lbl]
         (= edge-lbl (loom-label/label scg a b))

      's1 "foo" '(if-value $s1 (= $s1 1) false)
      's1 :Unset '(if-value $s1 (= $s1 0) false)
      's1 's2 '$s1=s2))

  ;; Edge cases!
  (let [spec (ssa/spec-to-ssa {} '{:spec-vars {:s "String" :s2 [:Maybe "String"]}
                                   :constraints [["c" (not= s $no-value)]]})
        scg (compute-string-comparison-graph spec)]
    ;; For mandatory string vars, comparison with $no-value should not
    ;; include an alts entry for :Unset
    (is (= {:alt-var '$s :alt-vals {}}
           (loom-label/label scg 's)))
    ;; Comparisons of mandatory vars with $no-value are allowed, but we know statically that they are false
    (is (= false (loom-label/label scg 's :Unset)))
    ;; We want a node and edge to :Unset for every optional string var, even
    ;; if it isn't found in an expression, to avoid too many special cases in bounds raising/lowering.
    (is (= {:alt-var '$s2 :alt-vals {:Unset 0}}
           (loom-label/label scg 's2)))
    (is (= '(if-value $s2 (= $s2 0) false)
           (loom-label/label scg 's2 :Unset)))))

(deftest test-lower-optional-string-vars-in-spec
  (let [spec (simplify-string-exprs (ssa/spec-to-ssa {} simple-optional-string-var-example))
        scg (compute-string-comparison-graph spec)
        lowered (lower-spec spec scg)]
    (is (= '{:spec-vars
             {:$s1 [:Maybe "Integer"] :$s2 [:Maybe "Integer"]
              :$s1=s2 "Boolean"}
             :constraints [["$all" (let [v1 (if-value $s1 (= $s1 0) false)]
                                     (and
                                      (if (not v1)
                                        $s1=s2
                                        (if-value $s2 (= $s2 0) false))
                                      (not (if-value $s2 (= $s2 1) false))
                                      (or (if-value $s1 (= $s1 1) false)
                                          v1)))]]
             :refines-to {}}
           (ssa/spec-from-ssa lowered)))))

(def optional-string-vars-example
  '{:spec-vars {:p "Boolean" :n "Integer" :s1 [:Maybe "String"] :s2 "String"}
    :constraints [["c1" (if p
                          (= s1 "foo")
                          (< n 5))]
                  ["c2" (= (= s1 s2) (> n 5))]
                  ["c3" (or (= "foo" s2)
                            (= "bar" s2)
                            (= "baz" s2))]
                  ["c4" (and (<= 0 n) (<= n 10))]]})

(deftest test-lower-spec-bound-with-optional-string-vars
  (let [spec (simplify-string-exprs (ssa/spec-to-ssa {} simple-optional-string-var-example))
        scg (compute-string-comparison-graph spec)
        base-bounds {:$s1 {:$in #{:Unset 0 1}} :$s2 {:$in #{:Unset 0 1}}}]
    (are [in out]
         (= (merge base-bounds out) (lower-spec-bound in scg))

      {} {}
      {:s1 :Unset} {:$s1 0}))

  (let [spec (simplify-string-exprs (ssa/spec-to-ssa {} optional-string-vars-example))
        scg (compute-string-comparison-graph spec)
        base-bounds {:$s1 {:$in #{:Unset 0 1}} :$s2 {:$in #{:Unset 0 1 2}}}]
    (are [in out]
         (= (merge base-bounds out) (lower-spec-bound in scg))

      {} {}
      {:s1 :String} {:$s1 {:$in #{:Unset 1}}}
      {:s1 {:$in #{:String}}} {:$s1 {:$in #{:Unset 1}}})))

(deftest test-raise-spec-bound-with-optional-string-vars
  (let [spec (simplify-string-exprs (ssa/spec-to-ssa {} simple-optional-string-var-example))
        scg (compute-string-comparison-graph spec)]
    (are [in out]
         (= out (try (raise-spec-bound in scg {})
                     (catch ContradictionException ex
                       :contradiction)))

      {:$s1 {:$in #{0 1}}, :$s2 {:$in #{0 :Unset}}, :$s1=s2 {:$in #{true false}}}
      {:s1 {:$in #{:Unset "foo"}} :s2 :String}))

  (let [spec (ssa/spec-to-ssa {} '{:spec-vars {:s1 "String" :s2 [:Maybe "String"]}})
        scg (compute-string-comparison-graph spec)]
    (is (= {:s1 :String :s2 {:$in #{:Unset :String}}}
           (raise-spec-bound {:$s1 :Unset :$s2 {:$in #{0 :Unset}}} scg {})))))

(deftest test-propagate-optional-string-vars
  (let [spec (ssa/spec-to-ssa {} simple-optional-string-var-example)]
    (s/without-fn-validation
     (are [in out]
          (= out (prop-strings/propagate spec in))

       {} {:s1 {:$in #{"foo" :Unset}}, :s2 :String}
       {:s1 "foo"} {:s1 "foo" :s2 "foo"}
       {:s1 :Unset} {:s1 :Unset :s2 "bar"}))))
