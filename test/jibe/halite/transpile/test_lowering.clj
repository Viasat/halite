;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.test-lowering
  (:require [jibe.halite :as halite]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.transpile.lowering :as lowering]
            [jibe.halite.transpile.rewriting :as rewriting]
            [jibe.halite.transpile.simplify :refer [simplify]]
            [jibe.halite.transpile.ssa :as ssa :refer [Derivations]]
            [jibe.halite.transpile.util :refer [fixpoint]]
            [schema.core :as s]
            [schema.test])
  (:use clojure.test))

(use-fixtures :once schema.test/validate-schemas)

(defn- print-trace-item [{:keys [rule op pruned-ids form form']}]
  (if rule
    (println (format "%s:  %s\n |->%s%s"
                     rule form (apply str (repeat (dec (count rule)) \space)) form'))
    (println "--- prune" (count pruned-ids) "---")))

(defn- print-trace-summary* [trace]
  (doseq [item trace]
    (print-trace-item item)))

(defmacro ^:private print-trace-summary
  [body spec-id]
  `(rewriting/with-tracing [traces#]
     (let [spec-id# ~spec-id
           result# ~body]
       (print-trace-summary* (get @traces# spec-id#))
       result#)))

(defn- troubleshoot* [trace]
  (let [last-item (last trace)]
    (clojure.pprint/pprint
     (->> trace
          (map (fn [{:keys [op] :as item}]
                 (condp = op
                   :rewrite (select-keys item [:rule :form :form'])
                   :prune (select-keys item [:pruned]))))))
    (when-let [spec-info (:spec-info last-item)]
      (clojure.pprint/pprint
       (ssa/spec-from-ssa spec-info)))
    (ssa/pprint-dgraph (:dgraph' last-item))))

(defmacro ^:private troubleshoot [body spec-id]
  `(lowering/with-tracing [traces#]
     (try
       ~body
       (catch Exception ex#
         (troubleshoot* (get (deref traces#) ~spec-id))
         (throw ex#)))))

(def flatten-do-expr #'lowering/flatten-do-expr)

(defn- rewrite-expr
  [ctx rewrite-fn form]
  (binding [ssa/*next-id* (atom 0), ssa/*hide-non-halite-ops* false]
    (let [[dgraph id] (ssa/form-to-ssa ctx form)
          new-expr (rewrite-fn (assoc ctx :dgraph dgraph) id (ssa/deref-id dgraph id))
          [dgraph id] (if (not= nil new-expr) (ssa/form-to-ssa (assoc ctx :dgraph dgraph) id new-expr) [dgraph id])
          scope (set (keys (halite-envs/scope (:tenv ctx))))]
      (ssa/form-from-ssa scope dgraph id))))

(defn- make-ssa-ctx
  ([] (make-ssa-ctx {}))
  ([{:keys [senv tenv env] :or {senv {} tenv {} env {}}}]
   {:senv (halite-envs/spec-env senv)
    :tenv (halite-envs/type-env tenv)
    :env env
    :dgraph {}}))

(defn- make-empty-ssa-ctx []
  (make-ssa-ctx))

(deftest test-flatten-do
  (let [ctx (make-empty-ssa-ctx)]
    (are [expr result]
        (= result (rewrite-expr ctx flatten-do-expr expr))

      '($do! 1 ($do! 2 3 4) 5) '($do! 1 2 3 4 5)
      '($do! 1 2 ($do! 3 4 5)) '($do! 1 2 3 4 5))))

(def bubble-up-do-expr #'lowering/bubble-up-do-expr)

(deftest test-bubble-up-do
  (let [ctx (make-empty-ssa-ctx)]
    (are [expr result]
        (= result (rewrite-expr ctx bubble-up-do-expr expr))

      '(+ 1 ($do! (div 1 0) 2) 3) '($do! (div 1 0) (+ 1 2 3))
      '(if true ($do! 1 2) 3) '(if true ($do! 1 2) 3)
      '(if ($do! 1 2 true) ($do! 9 8 3) 4) '($do! 1 2 (if true ($do! 9 8 3) 4))
      '{:$type :ws/A :an ($do! 1 2 3) :bn ($do! 4 5 6)} '($do! 1 2 4 5 {:$type :ws/A :an 3 :bn 6}))))

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
                             $20 (get $4 :bb)
                             $6 {:$type :ws/B :bn an :bb true}
                             $10 {:$type :ws/B :bn 4 :bb false}
                             $26 (get $4 :bn)]
                         (and
                          (and (= $20 (get $6 :bb))
                               (= $26 (get $6 :bn)))
                          (or (not= (get $10 :bb) $20)
                              (not= (get $10 :bn) $26))
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
                   :refines-to {}}
                  :ws/D
                  {:spec-vars {:b1 :ws/B, :b2 [:Maybe :ws/B]}
                   :constraints [["d1" (= b1 b2)]]
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
                  :ws/A ssa/spec-from-ssa :constraints)))
      (is (= '[["$all" (= b1 b2)]]
             (->> (ssa/build-spec-ctx senv :ws/D)
                  (fixpoint lower-instance-comparisons)
                  :ws/D ssa/spec-from-ssa :constraints))))))

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
         (let [$1 1 $2 true]
           (and (<= $1 10)
                (=> $2 (<= 0 $1))))
         false)
      #_(and (<= 1 10) (=> true (<= 0 1)))
      ;; -----------
      '(and (valid? {:$type :ws/B :bn an :bp false})
            (valid? {:$type :ws/B :bn 12 :bp (= an 1)}))
      '(let [$27 (and true true)]
         (and
          (if $27
            (let [$1 an, $3 false]
              (and (<= $1 10) (=> $3 (<= 0 $1))))
            false)
          (if (and true $27)
            (let [$9 (= an 1)]
              (let [$7 12]
                (and (<= $7 10) (=> $9 (<= 0 $7)))))
            false)))
      #_(and
         (and (<= an 10) (=> false (<= 0 an)))
         (and (<= 12 10) (=> (= an 1) (<= 0 12))))
      ;; -----------
      '(if (valid? {:$type :ws/B :bn an :bp false})
         {:$type :ws/B :bn an, :bp true}
         {:$type :ws/B :bn (+ 1 an) :bp (< 5 an)})
      '(if (if (and true true)
             (let [$1 an, $3 false]
               (and (<= $1 10) (=> $3 (<= 0 $1))))
             false)
         {:$type :ws/B, :bn an, :bp true}
         {:$type :ws/B, :bn (+ 1 an), :bp (< 5 an)})
      ;; -----------
      '(if (valid? {:$type :ws/B :bn an :bp false})
         (valid? {:$type :ws/B :bn an, :bp true})
         (valid? {:$type :ws/B :bn (+ 1 an) :bp (< 5 an)}))
      '(let [$31 (and true true)]
         (if (if $31
               (let [$1 an, $3 false]
                 (and (<= $1 10) (=> $3 (<= 0 $1))))
               false)
           (if $31
             (let [$1 an, $2 true]
               (and (<= $1 10) (=> $2 (<= 0 $1))))
             false)
           (if (and $31 $31)
             (let [$11 (+ 1 an), $13 (< 5 an)]
               (and (<= $11 10) (=> $13 (<= 0 $11))))
             false)))
      #_(if (and (<= an 10) (=> false (<= 0 an)))
          (and (<= an 10) (=> true (<= 0 an)))
          (and (<= (+ 1 an) 10) (=> (< 5 an) (<= 0 (+ 1 an)))))
      ;; -----------
      '(valid? (if (valid? {:$type :ws/B :bn an :bp false})
                 {:$type :ws/B :bn an, :bp true}
                 {:$type :ws/B :bn (+ 1 an) :bp (< 5 an)}))
      '(let [$28 (and true true)]
         (if (if $28 (if $28 (and true true (and $28 (and true $28))) true) false)
           (if (if $28 (let [$1 an $3 false] (and (<= $1 10) (=> $3 (<= 0 $1)))) false)
             (if $28 (let [$1 an $2 true] (and (<= $1 10) (=> $2 (<= 0 $1)))) false)
             (if (and $28 $28) (let [$9 (+ 1 an) $11 (< 5 an)] (and (<= $9 10) (=> $11 (<= 0 $9)))) false))
           false))
      #_(if (and (<= an 10) (=> false (<= 0 an)))
          (and (<= an 10) (=> true (<= 0 an)))
          (and (<= (+ 1 an) 10) (=> (< 5 an) (<= 0 (+ 1 an)))))
      ;; -----------
      '(valid? {:$type :ws/C :b {:$type :ws/B :bn an :bp (< an 15)}})
      '(if (if (and true (and true true))
             (let [$3 (< an 15)]
               (let [$1 an] (and (<= $1 10) (=> $3 (<= 0 $1)))))
             false)
         (let [$5 {:$type :ws/B, :bn an, :bp (< an 15)}]
           (not= (get $5 :bn) 4))
         false)
      #_(if (and (<= an 10) (=> (< an 15) (<= 0 an)))
          (not= (get {:$type :ws/B, :bn an, :bp (< an 15)} :bn) 4)
          false)
      ;; -----------
      '(valid? (get {:$type :ws/C :b {:$type :ws/B :bn an :bp (< an 15)}} :b))
      '(if (if (and true (and true true))
             (let [$3 (< an 15)]
               (let [$1 an]
                 (and (<= $1 10) (=> $3 (<= 0 $1)))))
             false)
         (let [$5 {:$type :ws/B, :bn an, :bp (< an 15)}]
           (not= (get $5 :bn) 4))
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
      (is (= '(let [$28 (get {:$type :ws/A, :an dn} :an)
                    $34 (get {:$type :ws/B, :bn (+ 1 $28)} :bn)]
                (and (= dm (get {:$type :ws/C, :cn $34} :cn))
                     (= dn (get {:$type :ws/B, :bn dn} :bn))))
             (-> sctx
                 (lower-refine-to)
                 :ws/D
                 (ssa/spec-from-ssa)
                 :constraints first second)))

      (is (= '(let [$40 (get {:$type :ws/A, :an dn} :an)
                    $88 (get {:$type :ws/B, :bn (+ 1 $40)} :bn)]
                (and (= dm (get {:$type :ws/C, :cn $88} :cn))
                     (= dn (get {:$type :ws/B, :bn dn} :bn))))
             (-> sctx
                 (lowering/lower)
                 :ws/D
                 (ssa/spec-from-ssa)
                 :constraints first second))))))

(def lower-no-value-comparison-expr #'lowering/lower-no-value-comparison-expr)

(deftest test-lower-no-value-comparisons
  (let [ctx (make-ssa-ctx {:tenv '{u [:Maybe :Integer] x :Integer p :Boolean}})]
    (are [expr lowered]
        (= lowered (rewrite-expr ctx lower-no-value-comparison-expr expr))

      '(= no-value no-value) true
      '(= no-value 1) false
      '(= no-value x) false
      '(= no-value u) '(= no-value u))))
