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

(defn- rewrite-expr-deeply
  [ctx rewrite-fn form]
  (binding [ssa/*next-id* (atom 0), ssa/*hide-non-halite-ops* false]
    (let [[dgraph id] (ssa/form-to-ssa ctx form)
          dgraph (rewriting/rewrite-dgraph (assoc ctx :dgraph dgraph) "<rule>" rewrite-fn)
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

      '(valid? {:$type :ws/B :bn 1 :bp (= $no-value (when (= an 1) 42))})
      '(if (and true (and true (if (and true true) (if (= an 1) true true) false)))
         (let [$8 (= $no-value (when (= an 1) 42))] (let [$1 1] (and (<= $1 10) (=> $8 (<= 0 $1)))))
         false)
      #_(let [$8 (= $no-value (when (= an 1) 42))]
          (let [$1 1]
            (and (<= $1 10) (=> $8 (<= 0 $1)))))

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

      '(= $no-value $no-value) true
      '(= $no-value 1) false
      '(= $no-value x) false
      '(= $no-value u) '(= $no-value u))))

(def lower-when-expr #'lowering/lower-when-expr)

(deftest test-lower-when
  (let [ctx (make-empty-ssa-ctx)]
    (are [expr result]
         (= result (rewrite-expr ctx lower-when-expr expr))

      '(when (= 1 2) (+ 3 4)) '(if (= 1 2) (+ 3 4) $no-value))))

(def lower-maybe-comparisons #'lowering/lower-maybe-comparisons)
(def lower-maybe-comparison-expr #'lowering/lower-maybe-comparison-expr)

(deftest test-lower-maybe-comparisons
  (let [ctx (make-ssa-ctx {:tenv '{u [:Maybe :Integer], v [:Maybe :Integer], w [:Maybe :Integer], x :Integer, y :Integer}})]
    (are [expr lowered]
         (= lowered (rewrite-expr ctx lower-maybe-comparison-expr expr))

      '(= v x)                '($do! x (if ($value? v) (= ($value! v) x) false))
      '(= x v y)              '($do! x y (if ($value? v) (= ($value! v) x y) false))
      '(= v $no-value)        '(if ($value? v) false true)
      '(= v $no-value x)      '($do! v x false)
      '(= v $no-value w)      '($do! w (if ($value? v) false (= $no-value w)))
      '(not= v x)             '($do! x (if ($value? v) (not= ($value! v) x) true))
      '(not= v $no-value)     '(if ($value? v) true false)
      '(not= v $no-value x)   '($do! v x true)
      '(not= x v y)           '($do! x y (if ($value? v) (not= ($value! v) x y) true))
      '(= v w)                '($do! w (if ($value? v) (= ($value! v) w) (if ($value? w) false true)))
      '(not= v w)             '($do! w (if ($value? v) (not= ($value! v) w) (if ($value? w) true false)))
      '(= x u y v w)          '($do! x y v w (if ($value? u) (= ($value! u) x y v w) (if ($value? v) false true)))
      '(= $no-value $no-value)'(= $no-value $no-value)
      ;; TODO: Show this working on (get) forms.

      ;; cannot be lowered as-is
      '(= x (if-value v (+ 1 v) w))
      '(= x (if ($value? v)
              (+ 1 ($value! v))
              w)))

;; Test that the rule iterates as expected
    (is (= '($do!
             x y v w
             (if ($value? u)
               ($do!
                ($value! u)
                x y w
                (if ($value? v)
                  ($do!
                   ($value! v) ($value! u) x y
                   (if ($value? w)
                     (= ($value! w) ($value! v) ($value! u) x y)
                     false))
                  (if ($value? w)
                    false
                    true)))
               (if ($value? v)
                 false
                 true)))
           (fixpoint #(rewrite-expr-deeply ctx lower-maybe-comparison-expr %) '(= x u y v w))))

    ;; TODO: Add tests of semantics preservation!
    ))

(def push-comparisons-into-maybe-ifs #'lowering/push-comparisons-into-maybe-ifs)

(deftest test-push-comparisons-into-maybe-ifs
  (binding [ssa/*next-id* (atom 0)
            ssa/*hide-non-halite-ops* false]
    (let [senv '{:ws/A
                 {:spec-vars {:v [:Maybe "Integer"], :w [:Maybe "Integer"], :x "Integer", :p "Boolean"}
                  :constraints []
                  :refines-to {}}}]
      (are [expr lowered]
           (= lowered
              (-> senv
                  (update-in [:ws/A :constraints] conj ["c" expr])
                  (halite-envs/spec-env)
                  (ssa/build-spec-ctx :ws/A)
                  (push-comparisons-into-maybe-ifs)
                  :ws/A
                  (ssa/spec-from-ssa)
                  :constraints first second))

        '(= x (if p v w))
        '(if p (= x v) (= x w))

        '(= x (if-value v (+ 1 v) w))
        '(if ($value? v)
           (= x (+ 1 ($value! v)))
           (= x w))

        '(= (if p v w)
            (if-value v (+ 1 v) w))
        '(let [$46 (if ($value? v)
                     (+ 1 ($value! v))
                     w)]
           (if p
             (= v $46)
             (= w $46))))

      (let [expr '(= (if p v w) (if-value v (+ 1 v) w))]
        (is (= '(if p
                  (if ($value? v)
                    (= v (+ 1 ($value! v)))
                    (= v w))
                  (if ($value? v)
                    (= w (+ 1 ($value! v)))
                    (= w w)))
               (-> senv
                   (update-in [:ws/A :constraints] conj ["c" expr])
                   (halite-envs/spec-env)
                   (ssa/build-spec-ctx :ws/A)
                   (->> (fixpoint push-comparisons-into-maybe-ifs))
                   :ws/A
                   (ssa/spec-from-ssa)
                   :constraints first second)))))))

(def push-if-value-into-if #'lowering/push-if-value-into-if)

(deftest test-push-if-value-into-if
  (binding [ssa/*next-id* (atom 0)
            ssa/*hide-non-halite-ops* false]
    (let [senv '{:ws/A
                 {:spec-vars {:v [:Maybe "Integer"], :w [:Maybe "Integer"], :x "Integer", :p "Boolean"}
                  :constraints []
                  :refines-to {}}}]

      (are [expr lowered]
           (= lowered
              (-> senv
                  (update-in [:ws/A :constraints] conj ["c" expr])
                  (halite-envs/spec-env)
                  (ssa/build-spec-ctx :ws/A)
                  (push-if-value-into-if)
                  :ws/A
                  (ssa/spec-from-ssa)
                  :constraints first second))

        '(let [n (if p x v)]
           (if-value n
                     (+ n 1)
                     0))
        '($do!
          (if p x v)
          (if p
            (+ x 1)
            (if ($value? v)
              (+ ($value! v) 1)
              0)))

        '(let [n (if p v x)]
           (if-value n
                     (+ n 1)
                     0))
        '($do! (if p v x)
               (if p
                 (if ($value? v)
                   (+ ($value! v) 1)
                   0)
                 (+ x 1)))

        '(let [n (if p w v)]
           (if-value n
                     (+ n 1)
                     0))
        '($do! (if p w v)
               (if p
                 (if ($value? w)
                   (+ ($value! w) 1)
                   0)
                 (if ($value? v)
                   (+ ($value! v) 1)
                   0)))))))

(deftest test-lowering-when-example
  (binding [ssa/*next-id* (atom 0)
            ssa/*hide-non-halite-ops* true]
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:spec-vars {:an "Integer", :aw [:Maybe "Integer"], :p "Boolean"}
                   :constraints [["a1" (= aw (when p an))]]
                   :refines-to {}}})
          sctx (ssa/build-spec-ctx senv :ws/A)]
      (is (= '(if p
                (if-value aw
                          (= aw an)
                          false)
                (if-value aw
                          false
                          true))
             (-> sctx
                 (lowering/lower)
                 :ws/A
                 (ssa/spec-from-ssa)
                 :constraints first second))))))

(deftest test-lowering-nested-optionals
  (schema.core/without-fn-validation
   (binding [ssa/*next-id* (atom 0)]
     (let [senv (halite-envs/spec-env
                 '{:ws/A
                   {:spec-vars {:b1 [:Maybe :ws/B], :b2 [:Maybe :ws/B], :ap "Boolean"}
                    :constraints [["a1" (= b1 b2)]
                                  ["a2" (=> ap (if-value b1 true false))]]
                    :refines-to {}}
                   :ws/B
                   {:spec-vars {:bx "Integer", :bw [:Maybe "Integer"], :bp "Boolean", :c1 :ws/C, :c2 [:Maybe :ws/C]}
                    :constraints [["b1" (= bw (when bp bx))]]
                    :refines-to {}}
                   :ws/C
                   {:spec-vars {:cx "Integer"
                                :cw [:Maybe "Integer"]}
                    :constraints []
                    :refines-to {}}
                   :ws/D
                   {:spec-vars {:dx "Integer"}
                    :constraints []
                    :refines-to {:ws/B {:expr {:$type :ws/B
                                               :bx (+ dx 1)
                                               :bw (when (= 0 (mod dx 2))
                                                     (div dx 2))
                                               :bp false
                                               :c1 {:$type :ws/C :cx dx :cw dx}
                                               :c2 {:$type :ws/C :cx dx :cw 12}}}}}})]
       ;; TODO: Turn this into a property-based test. It's not useful like this.
       (is (= '{:spec-vars {:ap "Boolean", :b1 [:Maybe :ws/B], :b2 [:Maybe :ws/B]}
                :constraints
                [["$all"
                  (and
                   (if-value b1
                             (let [$25 b1]
                               (if-value b2
                                         (let [$105 (get $25 :c2)
                                               $104 (get b2 :c2)
                                               $101 (get $25 :c1)
                                               $159 (get $101 :cw)
                                               $100 (get b2 :c1)
                                               $158 (get $100 :cw)
                                               $93 (get $25 :bw)
                                               $92 (get b2 :bw)]
                                           (and
                                            (= (get b2 :bp) (get $25 :bp))
                                            (if-value $92
                                                      (let [$114 $92]
                                                        (if-value $93 (= $93 $114) false))
                                                      (if-value $93 false true))
                                            (= (get b2 :bx) (get $25 :bx))
                                            (and
                                             (if-value $158
                                                       (let [$194 $158] (if-value $159 (= $159 $194) false))
                                                       (if-value $159 false true))
                                             (= (get $100 :cx) (get $101 :cx)))
                                            (if-value $104
                                                      (let [$127 $104]
                                                        (if-value $105
                                                                  (let [$236 (get $127 :cw) $235 (get $105 :cw)]
                                                                    (and
                                                                     (if-value $235
                                                                               (let [$249 $235] (if-value $236 (= $236 $249) false))
                                                                               (if-value $236 false true))
                                                                     (= (get $105 :cx) (get $127 :cx))))
                                                                  false))
                                                      (if-value $105 false true))))
                                         false))
                             (if-value b2 false true))
                   (=> ap (if-value b1 true false)))]]
                :refines-to {}}
              (-> senv
                  (ssa/build-spec-ctx :ws/A)
                  (lowering/lower)
                  :ws/A
                  (ssa/spec-from-ssa))))

       (is (= '{:spec-vars {:dx "Integer"}
                :constraints
                [["$all"
                  (let [$373 (= 0 (mod dx 2))]
                    (if (if $373 true true)
                      (let [$376 (if $373 (div dx 2) $no-value)
                            $378 (+ dx 1)
                            $379 {:$type :ws/C, :cw dx, :cx dx}
                            $381 {:$type :ws/C, :cw 12, :cx dx}]
                        (if $373 false true))
                      false))]],
                :refines-to {:ws/B {:expr {:$type :ws/B,
                                           :bp false,
                                           :bw (when (= 0 (mod dx 2)) (div dx 2)),
                                           :bx (+ dx 1),
                                           :c1 {:$type :ws/C, :cw dx, :cx dx},
                                           :c2 {:$type :ws/C, :cw 12, :cx dx}}}}}
              (-> senv
                  (ssa/build-spec-ctx :ws/D)
                  (lowering/lower)
                  :ws/D
                  (ssa/spec-from-ssa))))))))

(defonce ^:dynamic *results* (atom nil))
(defonce ^:dynamic *trace* (atom nil))

(deftest test-lowering-optionality
  (schema.core/without-fn-validation
   (binding [ssa/*next-id* (atom 0)
             ssa/*hide-non-halite-ops* true]
     (let [senv (halite-envs/spec-env
                 '{:ws/A
                   {:spec-vars {:b1 [:Maybe :ws/B], :b2 [:Maybe :ws/B], :aw [:Maybe "Integer"], :x "Integer", :p "Boolean"}
                    :constraints [["a1" (not= (if p b1 b2)
                                              (if-value aw
                                                        {:$type :ws/B :bw aw :bx (+ aw 1)}
                                                        (get {:$type :ws/C :b3 b1 :cw x} :b3)))]]
                    :refines-to {}}
                   :ws/B
                   {:spec-vars {:bv [:Maybe "Integer"], :bw [:Maybe "Integer"], :bx "Integer"}
                    :constraints []
                    :refines-to {}}
                   :ws/C
                   {:spec-vars {:b3 [:Maybe :ws/B], :cw "Integer"}
                    :constraints [["c1" (= (< 0 cw) (if-value b3 true false))]
                                  ["c2" (if-value b3
                                                  (let [bw (get b3 :bw)]
                                                    (if-value bw
                                                              (< cw bw)
                                                              false))
                                                  true)]]
                    :refines-to {}}})
           sctx (ssa/build-spec-ctx senv :ws/A)
           sctx' (rewriting/with-tracing [traces]
                   (let [result (lowering/lower sctx)]
                     (reset! *trace* (get @traces :ws/A))
                     result))
           int-range [-2 0 1 2]
           b-seq (for [bv? [true false]
                       bv (if bv? int-range [nil])
                       bw? [true false]
                       bw (if bw? int-range [nil])
                       bx int-range]
                   (cond-> {:$type :ws/B :bx bx}
                     bv? (assoc :bv bv)
                     bw? (assoc :bw bw)))
           a-seq (for [b1? [true false]
                       b1 (if b1? b-seq [nil])
                       b2? [true false]
                       b2 (if b2? b-seq [nil])
                       aw? [true false]
                       aw (if aw? int-range [nil])
                       x int-range
                       p [true false]]
                   (cond-> {:$type :ws/A :x x :p p}
                     b1? (assoc :b1 b1)
                     b2? (assoc :b2 b2)
                     aw? (assoc :aw aw)))
           senv' (ssa/build-spec-env sctx')
           tenv (halite-envs/type-env {})
           env (halite-envs/env {})
           check-a (fn [senv a]
                     (try
                       (halite/eval-expr senv tenv env (list 'valid? a))
                       (catch Exception ex
                         :runtime-error)))
           num-to-check 10000
           pprint-spec (fn [sctx spec-id]
                         (binding [ssa/*hide-non-halite-ops* false]
                           (clojure.pprint/pprint (ssa/spec-from-ssa (get sctx spec-id)))))]
       ;;(clojure.pprint/pprint (halite-envs/lookup-spec (ssa/build-spec-env sctx') :ws/A))
       (reset! *results* {:checked 0 :failed 0 :failures [] :valid 0 :invalid 0 :runtime-error 0})
       (doseq [a (take num-to-check (shuffle a-seq))]
         (let [r1 (check-a senv a)
               r2 (check-a senv' a)]
           (when-not (is (= r1 r2) (pr-str a))
             (println "Checking steps in trace...")
             (let [sctx' (assoc-in sctx [:ws/A :derivations] (:dgraph (first @*trace*)))
                   r3 (check-a (ssa/build-spec-env sctx') a)]
               (pprint-spec sctx' :ws/A)
               (when (not= r1 r3)
                 (println "Semantics different at start of trace")
                 (pprint-spec sctx' :ws/A)
                 (throw (ex-info "Stopping" {:r1 r1 :r2 r2 :inst a})))

               (doseq [{:keys [dgraph dgraph'] :as item} @*trace*]
                 (print-trace-item item)
                 (let [sctx' (assoc-in sctx [:ws/A :derivations] dgraph)
                       sctx'' (assoc-in sctx [:ws/A :derivations] dgraph')
                       r3 (check-a (ssa/build-spec-env sctx'') a)]
                   (when (not= r1 r3)
                     (pprint-spec sctx' :ws/A)
                     (pprint-spec sctx'' :ws/A)
                     (ssa/pprint-dgraph (-> sctx'' :ws/A :derivations))
                     (throw (ex-info "Stopping" {:r1 r1 :r2 r2 :inst a})))))))
           (swap! *results*
                  (fn [results]
                    (-> results
                        (update :checked inc)
                        (cond-> (= r1 r2)
                          (-> (update (condp = r1
                                        true :valid
                                        false :invalid
                                        :runtime-error :runtime-error) inc)))
                        (cond-> (not= r1 r2)
                          (-> (update :failed inc)
                              (update :failures conj {:inst a :r1 r1 :r2 r2}))))))
           (when (< 0 (:failed @*results*))
             (throw (ex-info "Lowered spec is not semantically equivalent!"
                             @*results*)))))
       ;; an assertion, to keep kaocha runner happy
       (is (= num-to-check (:checked @*results*)))))))

;; TODO: (get {:$type :ws/B} :notset) => :Unset
;; TODO: (from-ssa :Unset) => $no-value
;; TODO: Go back through the rules to see what needs to be added because of $do!

;; TODO: Test case where maybe comparisons lower to instance comparisons that lower to maybe comparisons.
;; We can handle the preservation of runtime errors by adding a $do internal form that form-from-ssa
;; turns into a let form for unbound nodes. Once that's in place, we should go back and fix
;; any runtime error preservation problems with other rewrite rules.

(comment
  "This example highlights the subtlety of RS^2's notion of abstractness."
  (defn example []
    (let [senv (halite-envs/spec-env
                '{:ws/A
                  {:abstract? true, :spec-vars {} :constraints [] :refines-to {}}
                  :ws/B
                  {:abstract? true, :spec-vars {} :constraints [] :refines-to {}}
                  :ws/X
                  {:spec-vars {:a :ws/A} :constraints [] :refines-to {}}
                  :ws/Y
                  {:spec-vars {:b :ws/B} :constraints [] :refines-to {}}
                  :ws/C
                  {:spec-vars {:x :ws/X, :y :ws/Y}
                   :constraints
                   [["c0" (refines-to? (get y :b) :ws/A)]
                    ["c1" (if (refines-to? (get y :b) :ws/A)
                            (not= x {:$type :ws/X :a (get y :b)})
                            false)]
                    ["c2" (if-let [x' (valid {:$type :ws/X :a (get y :b)})]
                            (not= x x'))]]
                   :refines-to {}}

                ;;;;;;;;;;;

                  :ws/Z
                  {:spec-vars {}
                   :constraints []
                   :refines-to
                   {:ws/A {:expr {:$type :ws/A}}
                    :ws/B {:expr {:$type :ws/B}}}}})
          tenv (halite-envs/type-env {'y :ws/Y})
          env (halite-envs/env {'y {:$type :ws/Y :b {:$type :ws/Z}}})
          expr '{:$type :ws/X :a (get y :b)}]
      (prn :expr expr)
      (prn :type (halite/type-check senv tenv expr))
      (prn :v (halite/eval-expr senv tenv env expr)))))
