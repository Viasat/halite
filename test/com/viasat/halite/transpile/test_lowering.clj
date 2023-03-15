;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.transpile.test-lowering
  (:require [clojure.pprint :as pprint]
            [clojure.test :refer :all]
            [com.viasat.halite :as halite]
            [com.viasat.halite.eval :as eval]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.transpile.lowering :as lowering]
            [com.viasat.halite.transpile.rewriting :as rewriting]
            [com.viasat.halite.transpile.simplify :as simplify]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.transpile.util :as transpile-util]
            [com.viasat.halite.var-types :as var-types]
            [schema.core :as s]
            [schema.test]))

(use-fixtures :once schema.test/validate-schemas)

(defn- troubleshoot* [trace]
  (let [last-item (last trace)]
    (pprint/pprint
     (->> trace
          (map (fn [{:keys [op] :as item}]
                 (condp = op
                   :rewrite (select-keys item [:rule :form :form'])
                   :prune (select-keys item [:pruned]))))))
    (when-let [spec-info (:spec-info last-item)]
      (pprint/pprint
       (ssa/spec-from-ssa spec-info)))
    (ssa/pprint-ssa-graph (:ssa-graph (:spec-info' last-item)))))

(defmacro ^:private troubleshoot [body spec-id]
  `(rewriting/with-tracing [traces#]
     (try
       ~body
       (catch Exception ex#
         (troubleshoot* (get (deref traces#) ~spec-id))
         (throw ex#)))))

(def flatten-do-expr #'lowering/flatten-do-expr)

(defn- rewrite-expr
  [ctx rewrite-fn form]
  (binding [ssa/*hide-non-halite-ops* false]
    (let [[ssa-graph id] (ssa/form-to-ssa ctx form)
          new-expr (rewrite-fn {:sctx {} :ctx (assoc ctx :ssa-graph ssa-graph)} id (ssa/deref-id ssa-graph id))
          [ssa-graph id] (if (not= nil new-expr) (ssa/form-to-ssa (assoc ctx :ssa-graph ssa-graph) new-expr) [ssa-graph id])
          scope (envs/tenv-keys (:tenv ctx))]
      (ssa/form-from-ssa scope ssa-graph id))))

(defn- make-ssa-ctx
  ([] (make-ssa-ctx {}))
  ([{:keys [senv tenv env] :or {senv {} tenv {} env {}}}]
   {:senv (envs/spec-env senv)
    :tenv (envs/type-env tenv)
    :env env
    :ssa-graph ssa/empty-ssa-graph
    :local-stack []}))

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
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env
               '{:ws/A
                 {:fields {:an :Integer}
                  :constraints #{{:name "a1"
                                  :expr (let [b {:$type :ws/B :bn 12 :bb true}]
                                          (and
                                           (= b {:$type :ws/B :bn an :bb true})
                                           (not= {:$type :ws/B :bn 4 :bb false} b)
                                           (= an 45)))}}}
                 :ws/B
                 {:fields {:bn :Integer :bb :Boolean}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]

    (is (= '[["$all" (let [v1 {:bb true, :bn 12, :$type :ws/B}]
                       (and
                        (and (= (get v1 :bb) (get {:bb true, :bn an, :$type :ws/B} :bb))
                             (= (get v1 :bn) (get {:bb true, :bn an, :$type :ws/B} :bn)))
                        (or (not= (get {:bb false, :bn 4, :$type :ws/B} :bb)
                                  (get v1 :bb))
                            (not= (get {:bb false, :bn 4, :$type :ws/B} :bn)
                                  (get v1 :bn)))
                        (= an 45)))]]
           (-> sctx lower-instance-comparisons :ws/A ssa/spec-from-ssa :constraints)))

    ;; This is a correct output, but not what spec-from-ssa currently produces,
    ;; because it refuses to let-bind instance literals that weren't already let-bound.
    #_(is (= '[["$all" (let [v1 {:$type :ws/B :bn 12 :bb true}
                             v2 (get v1 :bb)
                             v3 {:$type :ws/B :bn an :bb true}
                             v4 {:$type :ws/B :bn 4 :bb false}
                             v5 (get v1 :bn)]
                         (and
                          (and (= v2 (get v3 :bb))
                               (= v5 (get v3 :bn)))
                          (or (not= (get v4 :bb) v2)
                              (not= (get v4 :bn) v5))
                          (= an 45)))]]
             (-> sctx lower-instance-comparisons :ws/A ssa/spec-from-ssa :constraints)))))

(deftest test-lower-comparisons-with-incompatible-types
  (let [senv (var-types/to-halite-spec-env
              '{:ws/A {:fields {:an :Integer}}
                :ws/B {:fields {:bn :Integer}}
                :ws/C {:fields {:a :ws/A :b :ws/B :ma [:Maybe :ws/A] :mb [:Maybe :ws/B]}}})]
    (are [in out]
         (= out (-> senv
                    (update-in [:ws/C :constraints] conj ["c1" in])
                    envs/spec-env
                    (ssa/build-spec-ctx :ws/C)
                    (rewriting/rewrite-sctx lowering/lower-comparison-exprs-with-incompatible-types)
                    :ws/C
                    ssa/spec-from-ssa
                    :constraints first second))

      '(= 1 true) false
      '(not= 1 true) true
      '(= a ma) '(= a ma)
      '(= a b) false
      '(= ma mb) false
      '(= a mb) false
      '(not= 1 "foo") true)))

(deftest test-lower-instance-comparisons-for-composition
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env
               '{:ws/A
                 {:fields {:b1 :ws/B, :b2 :ws/B}
                  :constraints #{{:name "a1" :expr (not= b1 b2)}}}
                 :ws/B
                 {:fields {:c1 :ws/C, :c2 :ws/C}}
                 :ws/C
                 {:fields {:x :Integer :y :Integer}}
                 :ws/D
                 {:fields {:b1 :ws/B, :b2 [:Maybe :ws/B]}
                  :constraints #{{:name "d1" :expr (= b1 b2)}}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '[["$all" (let [v1 (get b1 :c2)
                           v2 (get b2 :c2)
                           v3 (get b2 :c1)
                           v4 (get b1 :c1)]
                       (or
                        (or
                         (not= (get v4 :x) (get v3 :x))
                         (not= (get v4 :y) (get v3 :y)))
                        (or
                         (not= (get v1 :x) (get v2 :x))
                         (not= (get v1 :y) (get v2 :y)))))]]
           (->> sctx
                (transpile-util/fixpoint lower-instance-comparisons)
                :ws/A ssa/spec-from-ssa :constraints)))
    (is (= '[["$all" (= b1 b2)]]
           (->> (ssa/build-spec-ctx senv :ws/D)
                (transpile-util/fixpoint lower-instance-comparisons)
                :ws/D ssa/spec-from-ssa :constraints)))))

(def replace-in-expr #'lowering/replace-in-expr)

(deftest test-replace-in-expr
  (are [replacements in out]
       (= out (replace-in-expr replacements in))

    '{a b, b a} '(= a (+ b 1)) '(= b (+ a 1))
    '{a b, b a} '{:a a, :b b}, '{:a b, :b a}
    '{a b, b a}, '(let [a b, b 1, c 2] (+ a b c)) '(let [a$1 a, b$1 1, c 2] (+ a$1 b$1 c))))

(deftest test-validity-guard
  (let [senv (var-types/to-halite-spec-env '{:ws/A
                                             {:fields {:an :Integer :b :ws/B}}
                                             :ws/B
                                             {:fields {:bn :Integer :bw [:Maybe :Integer] :c [:Maybe :ws/C]}
                                              :constraints #{{:name "b1" :expr (not= bn bw)}}}
                                             :ws/C
                                             {:fields {:cn :Integer}
                                              :constraints #{{:name "c1" :expr (< 12 cn)}}}
                                             :ws/D
                                             {:fields {:dn [:Maybe :Integer]}
                                              :constraints #{{:name "d1" :expr (if-value dn (< 0 dn) true)}}}})]

    (are [in out]
         (= out
            (let [sctx (-> senv
                           (update-in [:ws/A :constraints] conj ["c" in])
                           (envs/spec-env)
                           (ssa/build-spec-ctx :ws/A))]
              (lowering/validity-guard
               sctx
               (ssa/make-ssa-ctx sctx (:ws/A sctx))
               (->> (get-in sctx [:ws/A :constraints])
                    first
                    second))))

      true true
      42 true
      'an true
      '$no-value true
      '(+ 1 2 3) true
      '(if (= an 12) 13 14) true
      '(let [b {:$type :ws/B :bn 42}] true) '(not= $1 $no-value)

      '(let [b {:$type :ws/B :bn 42 :c {:$type :ws/C :cn an}}] true)
      '(if (< 12 $2) ($do! $3 (not= $1 $no-value)) false)

      '(let [c (if (< an 12) {:$type :ws/C :cn 10} {:$type :ws/C :cn 13})] true)
      '(if $3 (< 12 $5) (< 12 $7))

      ;; When the constraint from :ws/D is inlined, it'll
      ;; have the form '(if ($value? $no-value) ... true), which should
      ;; be simplified in-place to just 'true.
      '(let [d {:$type :ws/D}] (< 1 an)) true

      '{:$type :ws/C :cn 0}
      '(< 12 $1)

      '(valid? {:$type :ws/C :cn 0})
      'true

      '(let [i {:$type :ws/C :cn 0}] (if-value i true false))
      '(and (< 12 $1) (if (< 12 $1) true false))

      '(let [i (valid? {:$type :ws/C :cn 0})] (if-value i true false))
      'true)))

(def lower-valid? #'lowering/lower-valid?)

(deftest test-lower-valid?
  (let [senv (var-types/to-halite-spec-env
              '{:ws/A
                {:fields {:an :Integer}}
                :ws/B
                {:fields {:bn :Integer, :bp :Boolean}
                 :constraints #{{:name "b1" :expr (<= bn 10)}
                                {:name "b2" :expr (=> bp (<= 0 bn))}}}
                :ws/C
                {:fields {:b :ws/B}
                 :constraints #{{:name "c1" :expr (not= (get b :bn) 4)}}}})]
    (are [expr lowered-expr]
         (= lowered-expr
            (binding [ssa/*hide-non-halite-ops* false]
              (-> senv
                  (update-in [:ws/A :constraints] conj ["c" expr])
                  (envs/spec-env)
                  (ssa/build-spec-ctx :ws/A)
                  (lower-valid?)
                  :ws/A
                  (ssa/spec-from-ssa)
                  :constraints
                  first second)))

      true true

      '(valid? {:$type :ws/B :bn 1 :bp (= $no-value (when (= an 1) 42))})
      '(and (=> (= $no-value (if (= an 1) 42 $no-value)) (<= 0 1)) (<= 1 10))

      '(valid? {:$type :ws/B :bn 1 :bp true})
      '(and (=> true (<= 0 1)) (<= 1 10))
      ;; -----------
      '(and (valid? {:$type :ws/B :bn an :bp false})
            (valid? {:$type :ws/B :bn 12 :bp (= an 1)}))
      '(and
        (and (=> false (<= 0 an)) (<= an 10))
        (and (=> (= an 1) (<= 0 12)) (<= 12 10)))
      ;; -----------
      '(if (valid? {:$type :ws/B :bn an :bp false})
         {:$type :ws/B :bn an, :bp true}
         {:$type :ws/B :bn (+ 1 an) :bp (< 5 an)})
      '(if (and (=> false (<= 0 an)) (<= an 10))
         {:$type :ws/B :bn an, :bp true}
         {:$type :ws/B :bn (+ 1 an) :bp (< 5 an)})
      ;; -----------
      '(if (valid? {:$type :ws/B :bn an :bp false})
         (valid? {:$type :ws/B :bn an, :bp true})
         (valid? {:$type :ws/B :bn (+ 1 an) :bp (< 5 an)}))
      '(let [v1 (<= 0 an) v2 (<= an 10)]
         (if (and (=> false v1) v2)
           (and (=> true v1) v2)
           (let [v3 (+ 1 an)]
             (and (=> (< 5 an) (<= 0 v3)) (<= v3 10)))))
      ;; -----------
      '(valid? (if (valid? {:$type :ws/B :bn an :bp false})
                 {:$type :ws/B :bn an, :bp true}
                 {:$type :ws/B :bn (+ 1 an) :bp (< 5 an)}))
      '(let [v1 (<= an 10) v2 (<= 0 an)]
         (if (and (=> false v2) v1)
           (and (=> true v2) v1)
           (let [v3 (+ 1 an)]
             (and (=> (< 5 an) (<= 0 v3)) (<= v3 10)))))
      ;; -----------
      '(valid? {:$type :ws/C :b {:$type :ws/B :bn an :bp (< an 15)}})
      '(let [v1 (< an 15)]
         (if (and (=> v1 (<= 0 an)) (<= an 10))
           (not= (get {:$type :ws/B, :bn an, :bp v1} :bn) 4)
           false))
      ;; -----------
      '(valid? (get {:$type :ws/C :b {:$type :ws/B :bn an :bp (< an 15)}} :b))
      '(let [v1 (< an 15)]
         (if (and (=> v1 (<= 0 an)) (<= an 10))
           (not= (get {:$type :ws/B, :bn an, :bp v1} :bn) 4)
           false)))))

(def push-gets-into-ifs #'lowering/push-gets-into-ifs)

(deftest test-push-gets-into-ifs
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env
               '{:ws/A
                 {:fields {:ab :Boolean}
                  :constraints #{{:name "a1" :expr (not= 1 (get (if ab {:$type :ws/B :bn 2} {:$type :ws/B :bn 1}) :bn))}}}
                 :ws/B
                 {:fields {:bn :Integer}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '[["$all" (not= 1 (if ab
                               (get {:$type :ws/B :bn 2} :bn)
                               (get {:$type :ws/B :bn 1} :bn)))]]
           (-> sctx push-gets-into-ifs :ws/A ssa/spec-from-ssa :constraints)))))

(deftest test-push-gets-into-nested-ifs
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env '{:ws/A
                                              {:fields {:b1 :ws/B, :b2 :ws/B, :b3 :ws/B, :b4 :ws/B, :a :Boolean, :b :Boolean}
                                               :constraints #{{:name "a1" :expr (= 12 (get (if a (if b b1 b2) (if b b3 b4)) :n))}}}
                                              :ws/B
                                              {:fields {:n :Integer}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '[["$all" (= 12 (if a (if b (get b1 :n) (get b2 :n)) (if b (get b3 :n) (get b4 :n))))]]
           (->> sctx (transpile-util/fixpoint push-gets-into-ifs)
                :ws/A ssa/spec-from-ssa :constraints)))))

(deftest test-push-gets-into-ifs-ignores-nothing-branches
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env
               '{:ws/A {:fields {:b :ws/B :ap :Boolean}
                        :constraints #{{:name "a1" :expr (get (if ap b (error "nope")) :bp)}}}
                 :ws/B {:fields {:bp :Boolean}}}))]
    (is (= '(if ap (get b :bp) (error "nope"))
           (-> senv
               (ssa/build-spec-ctx :ws/A)
               push-gets-into-ifs
               :ws/A
               ssa/spec-from-ssa
               :constraints first second)))))

(def cancel-get-of-instance-literal #'lowering/cancel-get-of-instance-literal)

(deftest test-cancel-get-of-instance-literal
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env
               '{:ws/A
                 {:fields {:an :Integer :b :ws/B}
                  :constraints #{{:name "a1" :expr (< an (get (get {:$type :ws/B :c {:$type :ws/C :cn (get {:$type :ws/C :cn (+ 1 an)} :cn)}} :c) :cn))}
                                 {:name "a2" :expr (= (get (get b :c) :cn)
                                                      (get {:$type :ws/C :cn 12} :cn))}
                                 {:name "a3" :expr (let [cmn (get {:$type :ws/C, :cn 8} :cmn)]
                                                     (if-value cmn (< cmn 3) false))}}}
                 :ws/B
                 {:fields {:c :ws/C}}
                 :ws/C
                 {:fields {:cn :Integer, :cmn [:Maybe :Integer]}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '[["$all" (and
                      (= (get (get b :c) :cn) 12)
                      (let [v1 $no-value] (if-value v1 (< v1 3) false))
                      (< an (+ 1 an)))]]
           (->> sctx (transpile-util/fixpoint cancel-get-of-instance-literal) :ws/A ssa/spec-from-ssa :constraints)))))

(deftest test-eliminate-runtime-constraint-violations
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env
               '{:ws/A
                 {:fields {:an :Integer}
                  :constraints #{{:name "a1" :expr (< an 10)}
                                 {:name "a2" :expr (< an (get {:$type :ws/B :bn (+ 1 an)} :bn))}}}
                 :ws/B
                 {:fields {:bn :Integer}
                  :constraints #{{:name "b1" :expr (< 0 (get {:$type :ws/C :cn bn} :cn))}}}
                 :ws/C
                 {:fields {:cn :Integer}
                  :constraints #{{:name "c1" :expr (= 0 (mod cn 2))}}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '(let [v1 (+ 1 an)]
              (and (if (if (= 0 (mod v1 2))
                         (< 0 (get {:$type :ws/C, :cn v1} :cn))
                         false)
                     (< an (get {:$type :ws/B, :bn v1} :bn))
                     false)
                   (< an 10)))
           (-> sctx
               (lowering/eliminate-runtime-constraint-violations)
               (simplify/simplify)
               :ws/A
               (ssa/spec-from-ssa)
               :constraints first second)))))

(deftest test-eliminate-runtime-constraint-violations-and-if-value
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env
               '{:ws/A
                 {:fields {:ap [:Maybe :Boolean]}
                  :constraints #{{:name "a1" :expr (let [b {:$type :ws/B :bp ap}]
                                                     true)}}}
                 :ws/B
                 {:fields {:bp [:Maybe :Boolean]}
                  :constraints #{{:name "bp" :expr (if-value bp (if bp false true) true)}}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '(if (if-value ap (if ap false true) true) (let [v1 {:$type :ws/B, :bp ap}] true) false)
           (rewriting/with-tracing [traces]
             (-> sctx
                 (lowering/eliminate-runtime-constraint-violations)
                 :ws/A
                 (ssa/spec-from-ssa)
                 :constraints first second))))))

(defn senv-eval [senv expr]
  (eval/eval-expr* {:senv (update-vals senv ssa/spec-from-ssa)
                    :env (envs/env {})}
                   expr))

(deftest test-eliminate-runtime-constraint-violations-vs-eval
  (let [senv '{:ws/A {:fields {:av :Boolean}
                      :constraints [["av?" av]]}
               :ws/B {:fields {:bv :Boolean}}}
        with-bconst (fn [bconst]
                      (ssa/spec-map-to-ssa
                       (assoc-in senv [:ws/B :constraints] [["bconst" bconst]])))]

    (s/with-fn-validation
      (are [bconst expr result]
           ;; eliminate-runtime-constraint-violations shouldn't change the
           ;; result of any expression that doesn't throw:
           (= (senv-eval (with-bconst bconst) expr)
              (senv-eval (-> (with-bconst bconst)
                             lowering/eliminate-runtime-constraint-violations)
                         expr)
              result)

        '(let [i {:$type :ws/A :av bv}] true)
        '(valid? {:$type :ws/B, :bv true})
        true

        '(let [i {:$type :ws/A :av bv}] true)
        '(valid? {:$type :ws/B, :bv false})
        false

        '(let [i (valid? {:$type :ws/A :av bv})] true)
        '(valid? {:$type :ws/B, :bv true})
        true

        '(let [i (valid? {:$type :ws/A :av bv})] true)
        '(valid? {:$type :ws/B, :bv false})
        true))))

(deftest test-push-refine-to-into-if
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env
               '{:ws/W {:fields {:wn :Integer}}
                 :ws/A {:refines-to {:ws/W {:expr {:$type :ws/W :wn 1}}}}
                 :ws/B {:refines-to {:ws/W {:expr {:$type :ws/W :wn 2}}}}
                 :ws/C {:fields {:a :ws/A :b :ws/B :p :Boolean}
                        :constraints #{{:name "c1" :expr (< 0 (get (refine-to (if p a b) :ws/W) :wn))}}}}))]
    (is (= '(< 0 (get (if p (refine-to a :ws/W) (refine-to b :ws/W)) :wn))
           (-> senv
               (ssa/build-spec-ctx :ws/C)
               (rewriting/rewrite-sctx lowering/push-refine-to-into-if)
               :ws/C
               ssa/spec-from-ssa
               :constraints first second)))))

(def lower-no-value-comparison-expr #'lowering/lower-no-value-comparison-expr)

(deftest test-lower-no-value-comparisons
  (let [ctx (make-ssa-ctx {:tenv '{u [:Maybe :Integer] x :Integer p :Boolean}})]
    (are [expr lowered]
         (= lowered (rewrite-expr ctx lower-no-value-comparison-expr expr))

      '(= $no-value $no-value) true
      '(= $no-value 1) '($do! 1 false)
      '(= $no-value x) '($do! x false)
      '(= $no-value u) '(= $no-value u)
      '(= (if p $no-value (error "foo")) $no-value) '($do! (if p $no-value (error "foo")) true)
      '(= (if p $no-value (error "foo")) x) '($do! (if p $no-value (error "foo")) x false))))

(def lower-maybe-comparisons #'lowering/lower-maybe-comparisons)
(def lower-maybe-comparison-expr #'lowering/lower-maybe-comparison-expr)

(deftest test-lower-maybe-comparisons
  (let [sctx {:ws/A {:fields {:u [:Maybe :Integer], :v [:Maybe :Integer], :w [:Maybe :Integer], :x :Integer, :y :Integer}
                     :ssa-graph ssa/empty-ssa-graph}}
        ctx (ssa/make-ssa-ctx sctx (:ws/A sctx))]
    (are [expr lowered]
         (= lowered
            (let [[ssa-graph cid] (ssa/form-to-ssa ctx expr)]
              (-> sctx
                  (update :ws/A assoc :ssa-graph ssa-graph :constraints [["c" cid]])
                  (lower-maybe-comparisons)
                  :ws/A
                  (#(binding [ssa/*hide-non-halite-ops* false] (ssa/spec-from-ssa %)))
                  :constraints
                  first second)))

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
      '(= $no-value $no-value) '(= $no-value $no-value)
      ;; TODO: Show this working on (get) forms.

      ;; cannot be lowered as-is
      '(= x (if-value v (+ 1 v) w))
      '(= x (if ($value? v)
              (+ 1 ($value! v))
              w)))

    ;; Test that the rule iterates as expected
    (let [[ssa-graph cid] (ssa/form-to-ssa ctx '(= x u y v w))]
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
             (-> sctx
                 (update :ws/A assoc :ssa-graph ssa-graph :constraints [["c" cid]])
                 (->> (transpile-util/fixpoint lower-maybe-comparisons))
                 :ws/A
                 (#(binding [ssa/*hide-non-halite-ops* false] (ssa/spec-from-ssa %)))
                 :constraints
                 first second))))

    ;; TODO: Add tests of semantics preservation!
    ))

(deftest test-push-comparisons-into-nonprimitive-ifs
  (binding [ssa/*hide-non-halite-ops* false]
    (let [senv (var-types/to-halite-spec-env
                '{:ws/A
                  {:fields {:v [:Maybe :Integer], :w [:Maybe :Integer], :x :Integer, :p :Boolean}}})]
      (are [expr lowered]
           (= lowered
              (-> senv
                  (update-in [:ws/A :constraints] conj ["c" expr])
                  (envs/spec-env)
                  (ssa/build-spec-ctx :ws/A)
                  (rewriting/rewrite-sctx lowering/push-comparison-into-nonprimitive-if-in-expr)
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
        '(let [v1 (if ($value? v)
                    (+ 1 ($value! v))
                    w)]
           (if p
             (= v v1)
             (= w v1)))

        '(= 1 (if p x true))
        '(if p (= 1 x) (= 1 true)))

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
                   (envs/spec-env)
                   (ssa/build-spec-ctx :ws/A)
                   (->> (transpile-util/fixpoint #(rewriting/rewrite-sctx % lowering/push-comparison-into-nonprimitive-if-in-expr)))
                   :ws/A
                   (ssa/spec-from-ssa)
                   :constraints first second)))))))

(def push-if-value-into-if #'lowering/push-if-value-into-if)

(deftest test-push-if-value-into-if
  (binding [ssa/*hide-non-halite-ops* false]
    (let [senv (var-types/to-halite-spec-env
                '{:ws/A
                  {:fields {:v [:Maybe :Integer], :w [:Maybe :Integer], :x :Integer, :p :Boolean}}
                  :ws/B
                  {}})]

      (are [expr lowered]
           (= lowered
              (-> senv
                  (update-in [:ws/A :constraints] conj ["c" expr])
                  (envs/spec-env)
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
                   0)))

        '(let [b (if p {:$type :ws/B} $no-value)]
           (if-value b true false))
        '($do!
          (if p {:$type :ws/B} $no-value)
          (if p
            true
            false))))))

(deftest test-lowering-when-example
  (binding [ssa/*hide-non-halite-ops* true]
    (let [senv (envs/spec-env
                (var-types/to-halite-spec-env
                 '{:ws/A
                   {:fields {:an :Integer, :aw [:Maybe :Integer], :p :Boolean}
                    :constraints #{{:name "a1" :expr (= aw (when p an))}}}}))
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
   (let [senv (envs/spec-env
               (var-types/to-halite-spec-env
                '{:ws/A
                  {:fields {:b1 [:Maybe :ws/B], :b2 [:Maybe :ws/B], :ap :Boolean}
                   :constraints #{{:name "a1" :expr (= b1 b2)}
                                  {:name "a2" :expr (=> ap (if-value b1 true false))}}}
                  :ws/B
                  {:fields {:bx :Integer, :bw [:Maybe :Integer], :bp :Boolean, :c1 :ws/C, :c2 [:Maybe :ws/C]}
                   :constraints #{{:name "b1" :expr (= bw (when bp bx))}}}
                  :ws/C
                  {:fields {:cx :Integer
                            :cw [:Maybe :Integer]}}
                  :ws/D
                  {:fields {:dx :Integer}
                   :refines-to {:ws/B {:expr {:$type :ws/B
                                              :bx (+ dx 1)
                                              :bw (when (= 0 (mod dx 2))
                                                    (div dx 2))
                                              :bp false
                                              :c1 {:$type :ws/C :cx dx :cw dx}
                                              :c2 {:$type :ws/C :cx dx :cw 12}}}}}}))]
     ;; TODO: Turn this into a property-based test. It's not useful like this.
     (is (= '{:fields {:ap :Boolean, :b1 [:Maybe [:Instance :ws/B]], :b2 [:Maybe [:Instance :ws/B]]}
              :constraints [["$all"
                             (and
                              (=> ap (if-value b1 true false))
                              (if-value
                               b1
                               (if-value
                                b2
                                (let
                                 [v1 (get b2 :c1)
                                  v2 (get v1 :cw)
                                  v3 (get b1 :c1)
                                  v4 (get b2 :bw)
                                  v5 (get b2 :c2)]
                                  (and
                                   (= (get b2 :bp) (get b1 :bp))
                                   (if-value v4
                                             (let [v6 (get b1 :bw)]
                                               (if-value v6 (= v6 v4) false))
                                             (let [v6 (get b1 :bw)] (if-value v6 false true)))
                                   (= (get b2 :bx) (get b1 :bx))
                                   (and
                                    (if-value v2
                                              (let [v6 (get v3 :cw)]
                                                (if-value v6 (= v6 v2) false))
                                              (let [v6 (get v3 :cw)]
                                                (if-value v6 false true)))
                                    (= (get v1 :cx) (get v3 :cx)))
                                   (if-value v5
                                             (let [v6 (get b1 :c2)]
                                               (if-value v6
                                                         (let [v7 (get v6 :cw)]
                                                           (and
                                                            (if-value v7
                                                                      (let [v8 (get v5 :cw)]
                                                                        (if-value v8 (= v8 v7) false))
                                                                      (let [v8 (get v5 :cw)]
                                                                        (if-value v8 false true)))
                                                            (= (get v6 :cx) (get v5 :cx))))
                                                         false))
                                             (let [v6 (get b1 :c2)] (if-value v6 false true)))))
                                false)
                               (if-value b2 false true)))]]
              :refines-to {}}
            (-> senv
                (ssa/build-spec-ctx :ws/A)
                (lowering/lower)
                :ws/A
                (ssa/spec-from-ssa))))

     (is (= '{:fields {:dx :Integer}
              :constraints [["$all" true]],
              :refines-to {:ws/B {:expr {:$type :ws/B,
                                         :bp false,
                                         :bw (if (= 0 (mod dx 2)) (div dx 2) $no-value),
                                         :bx (+ dx 1),
                                         :c1 {:$type :ws/C, :cw dx, :cx dx},
                                         :c2 {:$type :ws/C, :cw 12, :cx dx}}}}}
            (-> senv
                (ssa/build-spec-ctx :ws/D)
                (lowering/lower)
                :ws/D
                (ssa/spec-from-ssa)))))))

(deftest test-eliminate-error-forms
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env '{:ws/A {:fields {:an :Integer :ap [:Maybe :Boolean]}
                                                     :constraints #{{:name "a1" :expr (if (< an 10)
                                                                                        (if (< an 1)
                                                                                          (error "an is too small")
                                                                                          (if-value ap ap false))
                                                                                        (= ap (when (< an 20)
                                                                                                (error "not big enough"))))}}}}))]
    (is (= '{:fields {:an :Integer :ap [:Maybe :Boolean]}
             :constraints [["$all"
                            (let [v1 (< an 10)]
                              (and
                               (if v1
                                 (if-value ap ap false)
                                 (= ap $no-value))
                               (not (and (<= 10 an) (< an 20)))
                               (not (and (< an 1) v1))))]]
             :refines-to {}}
           (-> senv
               (ssa/build-spec-ctx :ws/A)
               (lowering/eliminate-error-forms)
               :ws/A
               (ssa/spec-from-ssa)))))
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env
               '{:ws/A {:fields {:an :Integer :ap :Boolean}
                        :constraints #{{:name "a1" :expr (if ap
                                                           (if (< an 10)
                                                             (error "too small")
                                                             (not (= 42 (+ 1 (* an (error "too big"))))))
                                                           (= an 42))}}}}))]
    (is (= '(and (= an 42)
                 (not (and (<= 10 an) ap))
                 (not (and (< an 10) ap)))
           (-> senv
               (ssa/build-spec-ctx :ws/A)
               (lowering/eliminate-error-forms)
               :ws/A
               (ssa/spec-from-ssa)
               :constraints
               first second)))))

(deftest test-eliminate-error-forms-same-message
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env
               '{:ws/A {:fields {:an :Integer :ap :Boolean}
                        :constraints #{{:name "a1" :expr (if ap
                                                           (if (< an 10)
                                                             (error "fail")
                                                             (not (= 42 (+ 1 (* an (error "fail"))))))
                                                           (= an 42))}}}}))]
    ;; it is not clear why using the same error value in the code produces a slightly different form
    ;; but the resulting form is equivalent by de morgan's law
    (is (= '(and (= an 42)
                 (not (or (and (< an 10) ap)
                          (and (<= 10 an) ap))))
           (-> senv
               (ssa/build-spec-ctx :ws/A)
               (lowering/eliminate-error-forms)
               :ws/A
               (ssa/spec-from-ssa)
               :constraints
               first second)))))

(def rewrite-instance-valued-do-child #'lowering/rewrite-instance-valued-do-child)

(deftest test-rewrite-instance-valued-do-child
  (let [senv (var-types/to-halite-spec-env
              '{:ws/A
                {:fields {:ap :Boolean, :ab :ws/B, :an :Integer, :ad :ws/D}}
                :ws/B
                {:fields {:bn [:Maybe :Integer] :bp [:Maybe :Boolean]}}
                :ws/C
                {:fields {:cm :Integer, :cn :Integer}}
                :ws/D
                {:fields {:dn :Integer :dc :ws/C}}})]
    (are [in out]
         (= out
            (let [sctx (-> senv
                           (update-in [:ws/A :constraints] conj ["a1" (list '$do! in 'ap)])
                           envs/spec-env
                           (ssa/build-spec-ctx :ws/A))
                  ctx (ssa/make-ssa-ctx sctx (:ws/A sctx))
                  do-id (->> (get-in sctx [:ws/A :constraints])
                             first
                             second)
                  do-form (-> sctx :ws/A :ssa-graph (ssa/deref-id do-id) ssa/node-form)
                  child-id (second do-form)]
              (rewrite-instance-valued-do-child {:sctx sctx :ctx ctx} child-id)))

      'ab true

      '{:$type :ws/B} true

      '{:$type :ws/B :bn 12} true

      '{:$type :ws/B :bn 42 :bp false} true

      '{:$type :ws/B :bn (let [x (get ab :bn)] (if-value x (+ x 1) 0))}
      '($do! $11 true)

      '{:$type :ws/B :bn (+ 3 an) :bc {:$type :ws/C :cm (+ 1 an) :cn (+ 2 an)}}
      '($do! $3 $5 true $8 true)

      '(if ap {:$type :ws/B :bn (+ 1 an)} {:$type :ws/B :bn (+ 2 an)})
      '(if $1 ($do! $5 true) ($do! $8 true))

      '(get ad :dc) true

      '(get {:$type :ws/D :dc {:$type :ws/C :cm (+ an 1) :cn 2}} :dc)
      '($do! $3 true true)

      '(if ap {:$type :ws/B} $no-value) '(if $1 true $4)

      '($do! {:$type :ws/B :bn (+ 1 an)} {:$type :ws/B :bn (+ 2 an)})
      '($do! $3 true $6 true true))))

(def rewrite-no-value-do-child #'lowering/rewrite-no-value-do-child)

(deftest test-rewrite-no-value-do-child
  (let [senv (var-types/to-halite-spec-env '{:ws/A
                                             {:fields {:aw [:Maybe :Integer] :ap :Boolean :an :Integer}}})]
    (are [in out]
         (= out
            (let [sctx (-> senv
                           (update-in [:ws/A :constraints] conj ["a1" (list '$do! in 'true)])
                           envs/spec-env
                           (ssa/build-spec-ctx :ws/A))
                  ctx (ssa/make-ssa-ctx sctx (:ws/A sctx))
                  do-id (->> (get-in sctx [:ws/A :constraints])
                             first
                             second)
                  do-form (-> sctx :ws/A :ssa-graph (ssa/deref-id do-id) ssa/node-form)
                  child-id (second do-form)]
             ;;(ssa/pprint-ssa-graph (:ssa-graph ctx))
              (rewrite-no-value-do-child {:sctx sctx :ctx ctx} child-id)))

      'aw 'aw
      '$no-value true
      '(if ap aw an) '(if $1 aw $4)
      '(if ap aw $no-value) '(if $1 aw true)
      '($do! $no-value $no-value) '($do! true true)
      '(if ap (if (< an 0) $no-value 1) true) '(if $1 (if $5 true $8) $10))))

(deftest test-eliminate-unused-instance-valued-exprs-in-dos
  (let [senv (envs/spec-env
              (var-types/to-halite-spec-env
               '{:ws/A
                 {:fields {:ap :Boolean, :ab :ws/B, :an :Integer, :ad :ws/D}
                  :constraints #{{:name "a1" :expr (let [b (if ap
                                                             {:$type :ws/B :bn (+ 1 an)}
                                                             {:$type :ws/B :bn (+ 2 an)})]
                                                     true)}}}
                 :ws/B
                 {:fields {:bn [:Maybe :Integer] :bp [:Maybe :Boolean]}}
                 :ws/C
                 {:fields {:cm :Integer, :cn :Integer}}
                 :ws/D
                 {:fields {:dn :Integer :dc :ws/C}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '(let [v1 (if ap
                       (let [v1 (+ 1 an)]
                         true)
                       (let [v1 (+ 2 an)]
                         true))]
              true)
           (-> sctx
               (lowering/eliminate-unused-instance-valued-exprs-in-dos)
               :ws/A
               (ssa/spec-from-ssa)
               :constraints first second)))))

(defonce ^:dynamic *results* (atom nil))
(defonce ^:dynamic *trace* (atom nil))

(deftest test-lowering-optionality
  (schema.core/without-fn-validation
   (binding [ssa/*hide-non-halite-ops* true]
     (let [senv (envs/spec-env
                 (var-types/to-halite-spec-env
                  '{:ws/A
                    {:fields {:b1 [:Maybe :ws/B], :b2 [:Maybe :ws/B], :aw [:Maybe :Integer], :x :Integer, :p :Boolean}
                     :constraints #{{:name "a1" :expr (not= (if p b1 b2)
                                                            (if-value aw
                                                                      {:$type :ws/B :bw aw :bx (+ aw 1)}
                                                                      (get {:$type :ws/C :b3 b1 :cw x} :b3)))}}}
                    :ws/B
                    {:fields {:bv [:Maybe :Integer], :bw [:Maybe :Integer], :bx :Integer}}
                    :ws/C
                    {:fields {:b3 [:Maybe :ws/B], :cw :Integer}
                     :constraints #{{:name "c1" :expr (= (< 0 cw) (if-value b3 true false))}
                                    {:name "c2" :expr (if-value b3
                                                                (let [bw (get b3 :bw)]
                                                                  (if-value bw
                                                                            (< cw bw)
                                                                            false))
                                                                true)}}}}))
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
           tenv (envs/type-env {})
           env (envs/env {})
           check-a (fn [senv a]
                     (try
                       (halite/eval-expr senv tenv env (list 'valid? a))
                       (catch Exception ex
                         :runtime-error)))
           num-to-check 10000
           pprint-spec (fn [sctx spec-id]
                         (binding [ssa/*hide-non-halite-ops* false]
                           (pprint/pprint (ssa/spec-from-ssa (get sctx spec-id)))))]
       (reset! *results* {:checked 0 :failed 0 :failures [] :valid 0 :invalid 0 :runtime-error 0})
       (doseq [a (take num-to-check (shuffle a-seq))]
         (let [r1 (check-a senv a)
               r2 (check-a senv' a)]
           (when-not (is (= r1 r2) (pr-str a))
             (println "Checking steps in trace...")
             (let [sctx' (assoc sctx :ws/A (:spec-info (first @*trace*)))
                   r3 (check-a (ssa/build-spec-env sctx') a)]
               (pprint-spec sctx' :ws/A)
               (when (not= r1 r3)
                 (println "Semantics different at start of trace")
                 (pprint-spec sctx' :ws/A)
                 (throw (ex-info "Stopping" {:r1 r1 :r2 r2 :inst a})))

               (doseq [{:keys [spec-info spec-info'] :as item} @*trace*]
                 (rewriting/print-trace-item item)
                 (let [sctx' (assoc sctx :ws/A spec-info)
                       sctx'' (assoc sctx :ws/A spec-info')
                       r3 (check-a (ssa/build-spec-env sctx'') a)]
                   (when (not= r1 r3)
                     (pprint-spec sctx' :ws/A)
                     (pprint-spec sctx'' :ws/A)
                     (ssa/pprint-ssa-graph (-> sctx'' :ws/A :ssa-graph))
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
    (let [senv (envs/spec-env
                '{:ws/A
                  {:abstract? true}
                  :ws/B
                  {:abstract? true}
                  :ws/X
                  {:fields {:a :ws/A}}
                  :ws/Y
                  {:fields {:b :ws/B}}
                  :ws/C
                  {:fields {:x :ws/X, :y :ws/Y}
                   :constraints {"c0" (refines-to? (get y :b) :ws/A)
                                 "c1" (if (refines-to? (get y :b) :ws/A)
                                        (not= x {:$type :ws/X :a (get y :b)})
                                        false)
                                 "c2" (if-let [x' (valid {:$type :ws/X :a (get y :b)})]
                                        (not= x x'))}}

                ;;;;;;;;;;;

                  :ws/Z
                  {:refines-to
                   {:ws/A {:expr {:$type :ws/A}}
                    :ws/B {:expr {:$type :ws/B}}}}})
          tenv (envs/type-env {'y :ws/Y})
          env (envs/env {'y {:$type :ws/Y :b {:$type :ws/Z}}})
          expr '{:$type :ws/X :a (get y :b)}]
      (prn :expr expr)
      (prn :type (halite/type-check senv tenv expr))
      (prn :v (halite/eval-expr senv tenv env expr)))))

;; (run-tests)
