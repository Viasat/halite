;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.test-lowering
  (:require [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.transpile.lowering :as lowering]
            [jibe.halite.transpile.simplify :refer [simplify]]
            [jibe.halite.transpile.ssa :as ssa :refer [Derivations]]
            [jibe.halite.transpile.util :refer [fixpoint]]
            [schema.core :as s]
            [schema.test])
  (:use clojure.test))

(use-fixtures :once schema.test/validate-schemas)

(def lower-instance-comparisons #'lowering/lower-instance-comparisons)

(deftest test-lower-instance-comparisons
  (binding [ssa/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:an "Integer"}
                   :constraints [["a1" (let [b {:$type :ws/B :bn 12 :bb true}]
                                         (and
                                          (= b {:$type :ws/B :bn an :bb true})
                                          (not= {:$type :ws/B :bn 4 :bb false} b)
                                          (= an 45)))]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:bn "Integer" :bb "Boolean"}
                   :constraints []
                   :refines-to {}}})
          sctx (ssa/build-spec-ctx senv :ws/A)]
      (is (= '[["$all" (let [$4 {:$type :ws/B :bn 12 :bb true}
                             $24 (get $4 :bn)
                             $10 {:$type :ws/B :bn 4 :bb false}
                             $6 {:$type :ws/B :bn an :bb true}
                             $18 (get $4 :bb)]
                         (and
                          (and (= $18 (get $6 :bb))
                               (= $24 (get $6 :bn)))
                          (or (not= (get $10 :bb) $18)
                              (not= (get $10 :bn) $24))
                          (= an 45)))]]
             (-> sctx lower-instance-comparisons :ws/A ssa/spec-from-ssa :constraints))))))

(deftest test-lower-instance-comparisons-for-composition
  (binding [ssa/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:b1 :ws/B, :b2 :ws/B}
                   :constraints [["a1" (not= b1 b2)]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:c1 :ws/C, :c2 :ws/C}
                   :constraints []
                   :refines-to {}}
                  :ws/C
                  {:spec-vars {:x "Integer" :y "Integer"}
                   :constraints []
                   :refines-to {}}})
          sctx (ssa/build-spec-ctx senv :ws/A)]
      (is (= '[["$all" (let [$6 (get b2 :c1)
                             $5 (get b1 :c1)
                             $9 (get b1 :c2)
                             $10 (get b2 :c2)]
                         (or
                          (or
                           (not= (get $5 :x) (get $6 :x))
                           (not= (get $5 :y) (get $6 :y)))
                          (or
                           (not= (get $9 :x) (get $10 :x))
                           (not= (get $9 :y) (get $10 :y)))))]]
             (->> sctx
                  (fixpoint lower-instance-comparisons)
                  :ws/A ssa/spec-from-ssa :constraints))))))

(def lower-valid? #'lowering/lower-valid?)

(deftest test-lower-valid?
  (let [senv '{:ws/A
               {:spec-vars {:an "Integer"}
                :constraints []
                :refines-to {}}
               :ws/B
               {:spec-vars {:bn "Integer", :bp "Boolean"}
                :constraints [["b1" (<= bn 10)]
                              ("b2" (=> bp (<= 0 bn)))]
                :refines-to {}}
               :ws/C
               {:spec-vars {:b :ws/B}
                :constraints [["c1" (not= (get* b :bn) 4)]]
                :refines-to {}}}]
    (are [expr lowered-expr]
         (= lowered-expr
            (binding [ssa/*next-id* (atom 0)]
              (-> senv
                  (update-in [:ws/A :constraints] conj ["c" expr])
                  (halite-envs/spec-env)
                  (ssa/build-spec-ctx :ws/A)
                  (lower-valid?)
                  :ws/A
                  (ssa/spec-from-ssa)
                  :constraints
                  first second)))
      ;; The commented out forms below are hand-simplified versions
      ;; of the 'expected' values.

      true true

      '(valid? {:$type :ws/B :bn 1 :bp true})
      '(if (and true true)
         (and (<= 1 10) (=> true (<= 0 1)))
         false)
      #_(and (<= 1 10) (=> true (<= 0 1)))
      ;; -----------
      '(and (valid? {:$type :ws/B :bn an :bp false})
            (valid? {:$type :ws/B :bn 12 :bp (= an 1)}))
      '(let [$27 (and true true)]
         (and
          (if $27 (and (<= an 10) (=> false (<= 0 an))) false)
          (if (and true $27) (and (<= 12 10) (=> (= an 1) (<= 0 12))) false)))
      #_(and
         (and (<= an 10) (=> false (<= 0 an)))
         (and (<= 12 10) (=> (= an 1) (<= 0 12))))
      ;; -----------
      '(if (valid? {:$type :ws/B :bn an :bp false})
         {:$type :ws/B :bn an, :bp true}
         {:$type :ws/B :bn (+ 1 an) :bp (< 5 an)})
      '(if (if (and true true)
             (and (<= an 10) (=> false (<= 0 an)))
             false)
         {:$type :ws/B, :bn an, :bp true}
         {:$type :ws/B, :bn (+ 1 an), :bp (< 5 an)})
      ;; -----------
      '(if (valid? {:$type :ws/B :bn an :bp false})
         (valid? {:$type :ws/B :bn an, :bp true})
         (valid? {:$type :ws/B :bn (+ 1 an) :bp (< 5 an)}))
      '(let [$31 (and true true)]
         (if (if $31
               (and (<= an 10) (=> false (<= 0 an)))
               false)
           (if $31
             (and (<= an 10) (=> true (<= 0 an)))
             false)
           (if (and $31 $31)
             (let [$11 (+ 1 an)]
               (and (<= $11 10) (=> (< 5 an) (<= 0 $11))))
             false)))
      #_(if (and (<= an 10) (=> false (<= 0 an)))
          (and (<= an 10) (=> true (<= 0 an)))
          (and (<= (+ 1 an) 10) (=> (< 5 an) (<= 0 (+ 1 an)))))
      ;; -----------
      '(valid? (if (valid? {:$type :ws/B :bn an :bp false})
                 {:$type :ws/B :bn an, :bp true}
                 {:$type :ws/B :bn (+ 1 an) :bp (< 5 an)}))
      '(let [$28 (and true true)]
         (if (if $28 (if $28
                       (and $28 (and true $28))
                       true)
                 false)
           (if (if $28
                 (and (<= an 10) (=> false (<= 0 an)))
                 false)
             (if $28
               (and (<= an 10) (=> true (<= 0 an)))
               false)
             (if (and $28 $28)
               (let [$9 (+ 1 an)]
                 (and (<= $9 10) (=> (< 5 an) (<= 0 $9))))
               false))
           false))
      #_(if (and (<= an 10) (=> false (<= 0 an)))
          (and (<= an 10) (=> true (<= 0 an)))
          (and (<= (+ 1 an) 10) (=> (< 5 an) (<= 0 (+ 1 an)))))
      ;; -----------
      '(valid? {:$type :ws/C :b {:$type :ws/B :bn an :bp (< an 15)}})
      '(if (if (and true (and true true))
             (and (<= an 10) (=> (< an 15) (<= 0 an)))
             false)
         (not= (get {:$type :ws/B, :bn an, :bp (< an 15)} :bn) 4)
         false)
      #_(if (and (<= an 10) (=> (< an 15) (<= 0 an)))
          (not= (get {:$type :ws/B, :bn an, :bp (< an 15)} :bn) 4)
          false)
      ;; -----------
      '(valid? (get {:$type :ws/C :b {:$type :ws/B :bn an :bp (< an 15)}} :b))
      '(if (if (and true (and true true))
             (and (<= an 10) (=> (< an 15) (<= 0 an)))
             false)
         (not= (get {:$type :ws/B, :bn an, :bp (< an 15)} :bn) 4)
         false)
      #_(if (and (<= an 10) (=> (< an 15) (<= 0 an)))
          (not= (get {:$type :ws/B, :bn an, :bp (< an 15)} :bn) 4)
          false))))

(def push-gets-into-ifs #'lowering/push-gets-into-ifs)

(deftest test-push-gets-into-ifs
  (binding [ssa/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:ab "Boolean"}
                   :constraints [["a1" (not= 1 (get (if ab {:$type :ws/B :bn 2} {:$type :ws/B :bn 1}) :bn))]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:bn "Integer"}
                   :constraints []
                   :refines-to {}}})
          sctx (ssa/build-spec-ctx senv :ws/A)]
      (is (= '[["$all" (not= 1 (if ab
                                 (get {:$type :ws/B :bn 2} :bn)
                                 (get {:$type :ws/B :bn 1} :bn)))]]
             (-> sctx push-gets-into-ifs :ws/A ssa/spec-from-ssa :constraints))))))

(deftest test-push-gets-into-nested-ifs
  (binding [ssa/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:b1 :ws/B, :b2 :ws/B, :b3 :ws/B, :b4 :ws/B, :a "Boolean", :b "Boolean"}
                   :constraints [["a1" (= 12 (get (if a (if b b1 b2) (if b b3 b4)) :n))]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:n "Integer"}
                   :constraints []
                   :refines-to {}}})
          sctx (ssa/build-spec-ctx senv :ws/A)]
      (is (= '[["$all" (= 12 (if a (if b (get b1 :n) (get b2 :n)) (if b (get b3 :n) (get b4 :n))))]]
             (->> sctx (fixpoint push-gets-into-ifs)
                  :ws/A ssa/spec-from-ssa :constraints))))))

(def cancel-get-of-instance-literal #'lowering/cancel-get-of-instance-literal)

(deftest test-cancel-get-of-instance-literal
  (binding [ssa/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:an "Integer" :b :ws/B}
                   :constraints [["a1" (< an (get (get {:$type :ws/B :c {:$type :ws/C :cn (get {:$type :ws/C :cn (+ 1 an)} :cn)}} :c) :cn))]
                                 ["a2" (= (get (get b :c) :cn)
                                          (get {:$type :ws/C :cn 12} :cn))]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:c :ws/C}
                   :constraints [] :refines-to {}}
                  :ws/C
                  {:spec-vars {:cn "Integer"}
                   :constraints [] :refines-to {}}})
          sctx (ssa/build-spec-ctx senv :ws/A)]
      (is (= '[["$all" (and
                        (< an (+ 1 an))
                        (= (get (get b :c) :cn) 12))]]
             (->> sctx (fixpoint cancel-get-of-instance-literal) :ws/A ssa/spec-from-ssa :constraints))))))

(deftest test-eliminate-runtime-constraint-violations
  (binding [ssa/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:an "Integer"}
                   :constraints [["a1" (< an 10)]
                                 ["a2" (< an (get {:$type :ws/B :bn (+ 1 an)} :bn))]]
                   :refines-to {}}
                  :ws/B
                  {:spec-vars {:bn "Integer"}
                   :constraints [["b1" (< 0 (get {:$type :ws/C :cn bn} :cn))]]
                   :refines-to {}}
                  :ws/C
                  {:spec-vars {:cn "Integer"}
                   :constraints [["c1" (= 0 (mod cn 2))]]
                   :refines-to {}}})
          sctx (ssa/build-spec-ctx senv :ws/A)]
      (is (= '(let [$6 (+ 1 an)]
                (and (< an 10)
                     (if (if (= 0 (mod $6 2))
                           (< 0 (get {:$type :ws/C, :cn $6} :cn))
                           false)
                       (< an (get {:$type :ws/B, :bn $6} :bn))
                       false)))
             (-> sctx
                 (lowering/eliminate-runtime-constraint-violations)
                 (simplify)
                 :ws/A
                 (ssa/spec-from-ssa)
                 :constraints first second))))))

(def lower-refinement-constraints #'lowering/lower-refinement-constraints)

(deftest test-lower-refinement-constraints
  (binding [ssa/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:an "Integer"}
                   :constraints [["a1" (< an 10)]]
                   :refines-to {:ws/B {:expr {:$type :ws/B :bn (+ 1 an)}}}}
                  :ws/B
                  {:spec-vars {:bn "Integer"}
                   :constraints [["b1" (< 0 bn)]]
                   :refines-to {:ws/C {:expr {:$type :ws/C :cn bn}}}}
                  :ws/C
                  {:spec-vars {:cn "Integer"}
                   :constraints [["c1" (= 0 (mod cn 2))]]
                   :refines-to {}}})
          sctx (ssa/build-spec-ctx senv :ws/A)]

      (is (= '(and (< an 10)
                   (valid? {:$type :ws/B, :bn (+ 1 an)}))
             (-> sctx
                 (lower-refinement-constraints)
                 :ws/A
                 (ssa/spec-from-ssa)
                 :constraints first second)))

      (is (= '(let [$26 (+ 1 an)]
                (and (< an 10)
                     (and (< 0 $26)
                          (= 0 (mod $26 2)))))
             (-> sctx
                 (lowering/lower)
                 :ws/A
                 (ssa/spec-from-ssa)
                 :constraints first second))))))

(def lower-refine-to #'lowering/lower-refine-to)

(deftest test-lower-refine-to
  (binding [ssa/*next-id* (atom 0)]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:an "Integer"}
                   :constraints [["a1" (< an 10)]]
                   :refines-to {:ws/B {:expr {:$type :ws/B :bn (+ 1 an)}}}}
                  :ws/B
                  {:spec-vars {:bn "Integer"}
                   :constraints [["b1" (< 0 bn)]]
                   :refines-to {:ws/C {:expr {:$type :ws/C :cn bn}}}}
                  :ws/C
                  {:spec-vars {:cn "Integer"}
                   :constraints [["c1" (= 0 (mod cn 2))]]
                   :refines-to {}}
                  :ws/D
                  {:spec-vars {:dm "Integer", :dn "Integer"}
                   :constraints [["d1" (= dm (get (refine-to {:$type :ws/A :an dn} :ws/C) :cn))]
                                 ["d2" (= dn (get (refine-to {:$type :ws/B :bn dn} :ws/B) :bn))]]
                   :refines-to {}}})
          sctx (ssa/build-spec-ctx senv :ws/D)]
      (is (= '(and
               (= dm (get {:$type :ws/C :cn (get {:$type :ws/B :bn (+ 1 (get {:$type :ws/A :an dn} :an))} :bn)} :cn))
               (= dn (get {:$type :ws/B :bn dn} :bn)))
             (-> sctx
                 (lower-refine-to)
                 :ws/D
                 (ssa/spec-from-ssa)
                 :constraints first second)))

      (is (= '(and
               (= dm (get {:$type :ws/C, :cn (get {:$type :ws/B, :bn (+ 1 (get {:$type :ws/A, :an dn} :an))} :bn)} :cn))
               (= dn (get {:$type :ws/B, :bn dn} :bn)))
             (-> sctx
                 (lowering/lower)
                 :ws/D
                 (ssa/spec-from-ssa)
                 :constraints first second))))))
