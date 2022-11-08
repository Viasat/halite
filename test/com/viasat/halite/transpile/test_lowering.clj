;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.transpile.test-lowering
  (:require [com.viasat.halite :as halite]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.transpile.lowering :as lowering]
            [com.viasat.halite.transpile.rewriting :as rewriting :refer [rewrite-reachable-sctx rule]]
            [com.viasat.halite.transpile.simplify :refer [simplify]]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.transpile.util :refer [fixpoint]]
            [com.viasat.halite.var-type :as var-type]
            [schema.core :as s]
            [schema.test])
  (:use clojure.test))

(use-fixtures :once schema.test/validate-schemas)

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
          scope (set (keys (envs/scope (:tenv ctx))))]
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
              (var-type/to-halite-spec-env
               '{:ws/A
                 {:spec-vars {:an :Integer}
                  :constraints {"a1" (let [b {:$type :ws/B :bn 12 :bb true}]
                                       (and
                                        (= b {:$type :ws/B :bn an :bb true})
                                        (not= {:$type :ws/B :bn 4 :bb false} b)
                                        (= an 45)))}}
                 :ws/B
                 {:spec-vars {:bn :Integer :bb :Boolean}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '{"$all" (let [v1 {:$type :ws/B :bn 12 :bb true}
                          v2 (get v1 :bb)
                          v3 {:$type :ws/B :bn an :bb true}
                          v4 {:$type :ws/B :bn 4 :bb false}
                          v5 (get v1 :bn)]
                      (and
                       (and (= v2 (get v3 :bb))
                            (= v5 (get v3 :bn)))
                       (or (not= (get v4 :bb) v2)
                           (not= (get v4 :bn) v5))
                       (= an 45)))}
           (-> sctx lower-instance-comparisons :ws/A ssa/spec-from-ssa :constraints)))))

(deftest test-lower-comparisons-with-incompatible-types
  (let [senv (var-type/to-halite-spec-env
              '{:ws/A {:spec-vars {:an :Integer}}
                :ws/B {:spec-vars {:bn :Integer}}
                :ws/C {:spec-vars {:a :ws/A :b :ws/B :ma [:Maybe :ws/A] :mb [:Maybe :ws/B]}}})]
    (are [in out]
         (= out (-> senv
                    (update-in [:ws/C :constraints] assoc "c1" in)
                    envs/spec-env
                    (ssa/build-spec-ctx :ws/C)
                    (rewriting/rewrite-sctx lowering/lower-comparison-exprs-with-incompatible-types)
                    :ws/C
                    ssa/spec-from-ssa
                    :constraints first val))

      '(= 1 true) false
      '(not= 1 true) true
      '(= a ma) '(= a ma)
      '(= a b) false
      '(= ma mb) false
      '(= a mb) false
      '(not= 1 "foo") true)))

(deftest test-lower-instance-comparisons-for-composition
  (let [senv (envs/spec-env
              (var-type/to-halite-spec-env
               '{:ws/A
                 {:spec-vars {:b1 :ws/B, :b2 :ws/B}
                  :constraints {"a1" (not= b1 b2)}}
                 :ws/B
                 {:spec-vars {:c1 :ws/C, :c2 :ws/C}}
                 :ws/C
                 {:spec-vars {:x :Integer :y :Integer}}
                 :ws/D
                 {:spec-vars {:b1 :ws/B, :b2 [:Maybe :ws/B]}
                  :constraints {"d1" (= b1 b2)}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '{"$all" (let [v1 (get b1 :c2)
                          v2 (get b2 :c2)
                          v3 (get b2 :c1)
                          v4 (get b1 :c1)]
                      (or
                       (or
                        (not= (get v4 :x) (get v3 :x))
                        (not= (get v4 :y) (get v3 :y)))
                       (or
                        (not= (get v1 :x) (get v2 :x))
                        (not= (get v1 :y) (get v2 :y)))))}
           (->> sctx
                (fixpoint lower-instance-comparisons)
                :ws/A ssa/spec-from-ssa :constraints)))
    (is (= '{"$all" (= b1 b2)}
           (->> (ssa/build-spec-ctx senv :ws/D)
                (fixpoint lower-instance-comparisons)
                :ws/D ssa/spec-from-ssa :constraints)))))

(def replace-in-expr #'lowering/replace-in-expr)

(deftest test-replace-in-expr
  (are [replacements in out]
       (= out (replace-in-expr replacements in))

    '{a b, b a} '(= a (+ b 1)) '(= b (+ a 1))
    '{a b, b a} '{:a a, :b b}, '{:a b, :b a}
    '{a b, b a}, '(let [a b, b 1, c 2] (+ a b c)) '(let [a$1 a, b$1 1, c 2] (+ a$1 b$1 c))))

(deftest test-validity-guard
  (let [senv (var-type/to-halite-spec-env '{:ws/A
                                            {:spec-vars {:an :Integer :b :ws/B}}
                                            :ws/B
                                            {:spec-vars {:bn :Integer :bw [:Maybe :Integer] :c [:Maybe :ws/C]}
                                             :constraints {"b1" (not= bn bw)}}
                                            :ws/C
                                            {:spec-vars {:cn :Integer}
                                             :constraints {"c1" (< 12 cn)}}
                                            :ws/D
                                            {:spec-vars {:dn [:Maybe :Integer]}
                                             :constraints {"d1" (if-value dn (< 0 dn) true)}}})]
    (are [in out]
         (= out
            (let [sctx (-> senv
                           (update-in [:ws/A :constraints] assoc "c" in)
                           (envs/spec-env)
                           (ssa/build-spec-ctx :ws/A))]
              (lowering/validity-guard
               sctx
               (ssa/make-ssa-ctx sctx (:ws/A sctx))
               (->> (get-in sctx [:ws/A :constraints])
                    first
                    val))))

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
      '(let [d {:$type :ws/D}] (< 1 an)) true)))

(def lower-valid? #'lowering/lower-valid?)

(deftest test-lower-valid?
  (let [senv (var-type/to-halite-spec-env
              '{:ws/A
                {:spec-vars {:an :Integer}}
                :ws/B
                {:spec-vars {:bn :Integer, :bp :Boolean}
                 :constraints {"b1" (<= bn 10)
                               "b2" (=> bp (<= 0 bn))}}
                :ws/C
                {:spec-vars {:b :ws/B}
                 :constraints {"c1" (not= (get* b :bn) 4)}}})]
    (are [expr lowered-expr]
         (= lowered-expr
            (binding [ssa/*hide-non-halite-ops* false]
              (-> senv
                  (update-in [:ws/A :constraints] assoc "c" expr)
                  (envs/spec-env)
                  (ssa/build-spec-ctx :ws/A)
                  (lower-valid?)
                  :ws/A
                  (ssa/spec-from-ssa)
                  :constraints
                  first val)))

      true true

      '(valid? {:$type :ws/B :bn 1 :bp (= $no-value (when (= an 1) 42))})
      '(and (<= 1 10) (=> (= $no-value (when (= an 1) 42)) (<= 0 1)))

      '(valid? {:$type :ws/B :bn 1 :bp true})
      '(and (<= 1 10) (=> true (<= 0 1)))
      ;; -----------
      '(and (valid? {:$type :ws/B :bn an :bp false})
            (valid? {:$type :ws/B :bn 12 :bp (= an 1)}))
      '(and
        (and (<= an 10) (=> false (<= 0 an)))
        (and (<= 12 10) (=> (= an 1) (<= 0 12))))
      ;; -----------
      '(if (valid? {:$type :ws/B :bn an :bp false})
         {:$type :ws/B :bn an, :bp true}
         {:$type :ws/B :bn (+ 1 an) :bp (< 5 an)})
      '(if (and (<= an 10) (=> false (<= 0 an)))
         {:$type :ws/B :bn an, :bp true}
         {:$type :ws/B :bn (+ 1 an) :bp (< 5 an)})
      ;; -----------
      '(if (valid? {:$type :ws/B :bn an :bp false})
         (valid? {:$type :ws/B :bn an, :bp true})
         (valid? {:$type :ws/B :bn (+ 1 an) :bp (< 5 an)}))
      '(let [v1 (<= 0 an), v2 (<= an 10)]
         (if (and v2 (=> false v1))
           (and v2 (=> true v1))
           (let [v3 (+ 1 an)]
             (and (<= v3 10) (=> (< 5 an) (<= 0 v3))))))
      ;; -----------
      '(valid? (if (valid? {:$type :ws/B :bn an :bp false})
                 {:$type :ws/B :bn an, :bp true}
                 {:$type :ws/B :bn (+ 1 an) :bp (< 5 an)}))
      '(let [v1 (<= 0 an), v2 (<= an 10)]
         (if (and v2 (=> false v1))
           (and v2 (=> true v1))
           (let [v3 (+ 1 an)]
             (and (<= v3 10) (=> (< 5 an) (<= 0 v3))))))
      ;; -----------
      '(valid? {:$type :ws/C :b {:$type :ws/B :bn an :bp (< an 15)}})
      '(let [v1 (< an 15)]
         (if (and (<= an 10) (=> v1 (<= 0 an)))
           (not= (get {:$type :ws/B, :bn an, :bp v1} :bn) 4)
           false))
      ;; -----------
      '(valid? (get {:$type :ws/C :b {:$type :ws/B :bn an :bp (< an 15)}} :b))
      '(let [v1 (< an 15)]
         (if (and (<= an 10) (=> v1 (<= 0 an)))
           (not= (get {:$type :ws/B, :bn an, :bp v1} :bn) 4)
           false)))))

(def push-gets-into-ifs #'lowering/push-gets-into-ifs)

(deftest test-push-gets-into-ifs
  (let [senv (envs/spec-env
              (var-type/to-halite-spec-env
               '{:ws/A
                 {:spec-vars {:ab :Boolean}
                  :constraints {"a1" (not= 1 (get (if ab {:$type :ws/B :bn 2} {:$type :ws/B :bn 1}) :bn))}}
                 :ws/B
                 {:spec-vars {:bn :Integer}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '{"$all" (not= 1 (if ab
                              (get {:$type :ws/B :bn 2} :bn)
                              (get {:$type :ws/B :bn 1} :bn)))}
           (-> sctx push-gets-into-ifs :ws/A ssa/spec-from-ssa :constraints)))))

(deftest test-push-gets-into-nested-ifs
  (let [senv (envs/spec-env
              (var-type/to-halite-spec-env '{:ws/A
                                             {:spec-vars {:b1 :ws/B, :b2 :ws/B, :b3 :ws/B, :b4 :ws/B, :a :Boolean, :b :Boolean}
                                              :constraints {"a1" (= 12 (get (if a (if b b1 b2) (if b b3 b4)) :n))}}
                                             :ws/B
                                             {:spec-vars {:n :Integer}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '{"$all" (= 12 (if a (if b (get b1 :n) (get b2 :n)) (if b (get b3 :n) (get b4 :n))))}
           (->> sctx (fixpoint push-gets-into-ifs)
                :ws/A ssa/spec-from-ssa :constraints)))))

(deftest test-push-gets-into-ifs-ignores-nothing-branches
  (let [senv (envs/spec-env
              (var-type/to-halite-spec-env
               '{:ws/A {:spec-vars {:b :ws/B :ap :Boolean}
                        :constraints {"a1" (get (if ap b (error "nope")) :bp)}}
                 :ws/B {:spec-vars {:bp :Boolean}}}))]
    (is (= '(if ap (get b :bp) (error "nope"))
           (-> senv
               (ssa/build-spec-ctx :ws/A)
               push-gets-into-ifs
               :ws/A
               ssa/spec-from-ssa
               :constraints first val)))))

(def cancel-get-of-instance-literal #'lowering/cancel-get-of-instance-literal)

(deftest test-cancel-get-of-instance-literal
  (let [senv (envs/spec-env
              (var-type/to-halite-spec-env
               '{:ws/A
                 {:spec-vars {:an :Integer :b :ws/B}
                  :constraints {"a1" (< an (get (get {:$type :ws/B :c {:$type :ws/C :cn (get {:$type :ws/C :cn (+ 1 an)} :cn)}} :c) :cn))
                                "a2" (= (get (get b :c) :cn)
                                        (get {:$type :ws/C :cn 12} :cn))
                                "a3" (let [cmn (get {:$type :ws/C, :cn 8} :cmn)]
                                       (if-value cmn (< cmn 3) false))}}
                 :ws/B
                 {:spec-vars {:c :ws/C}}
                 :ws/C
                 {:spec-vars {:cn :Integer, :cmn [:Maybe :Integer]}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '{"$all" (and
                     (< an (+ 1 an))
                     (= (get (get b :c) :cn) 12)
                     (let [v1 $no-value] (if-value v1 (< v1 3) false)))}
           (->> sctx (fixpoint cancel-get-of-instance-literal) :ws/A ssa/spec-from-ssa :constraints)))))

(deftest test-eliminate-runtime-constraint-violations
  (let [senv (envs/spec-env
              (var-type/to-halite-spec-env
               '{:ws/A
                 {:spec-vars {:an :Integer}
                  :constraints {"a1" (< an 10)
                                "a2" (< an (get {:$type :ws/B :bn (+ 1 an)} :bn))}}
                 :ws/B
                 {:spec-vars {:bn :Integer}
                  :constraints {"b1" (< 0 (get {:$type :ws/C :cn bn} :cn))}}
                 :ws/C
                 {:spec-vars {:cn :Integer}
                  :constraints {"c1" (= 0 (mod cn 2))}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '(let [v1 (+ 1 an)]
              (and (< an 10)
                   (if (if (= 0 (mod v1 2))
                         (< 0 (get {:$type :ws/C, :cn v1} :cn))
                         false)
                     (< an (get {:$type :ws/B, :bn v1} :bn))
                     false)))
           (-> sctx
               (lowering/eliminate-runtime-constraint-violations)
               (simplify)
               :ws/A
               (ssa/spec-from-ssa)
               :constraints first val)))))

(deftest test-eliminate-runtime-constraint-violations-and-if-value
  (let [senv (envs/spec-env
              (var-type/to-halite-spec-env
               '{:ws/A
                 {:spec-vars {:ap [:Maybe :Boolean]}
                  :constraints {"a1" (let [b {:$type :ws/B :bp ap}]
                                       true)}}
                 :ws/B
                 {:spec-vars {:bp [:Maybe :Boolean]}
                  :constraints {"bp" (if-value bp (if bp false true) true)}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]
    (is (= '(if (if-value ap (if ap false true) true) (let [v1 {:$type :ws/B, :bp ap}] true) false)
           (rewriting/with-tracing [traces]
             (-> sctx
                 (lowering/eliminate-runtime-constraint-violations)
                 :ws/A
                 (ssa/spec-from-ssa)
                 :constraints first val))))))

(def lower-refinement-constraints #'lowering/lower-refinement-constraints)

(deftest test-lower-refinement-constraints
  (let [senv (envs/spec-env
              (var-type/to-halite-spec-env
               '{:ws/A
                 {:spec-vars {:an :Integer}
                  :constraints {"a1" (< an 10)}
                  :refines-to {:ws/B {:expr {:$type :ws/B :bn (+ 1 an)}}}}
                 :ws/B
                 {:spec-vars {:bn :Integer}
                  :constraints {"b1" (< 0 bn)}
                  :refines-to {:ws/C {:expr {:$type :ws/C :cn bn}}}}
                 :ws/C
                 {:spec-vars {:cn :Integer}
                  :constraints {"c1" (= 0 (mod cn 2))}}}))
        sctx (ssa/build-spec-ctx senv :ws/A)]

    (is (= '(and (< an 10)
                 (valid? {:$type :ws/B, :bn (+ 1 an)}))
           (-> sctx
               (lower-refinement-constraints)
               :ws/A
               (ssa/spec-from-ssa)
               :constraints first val)))

    (is (= '(let [v1 (+ 1 an)]
              (and (< an 10)
                   (and (< 0 v1)
                        (= 0 (mod v1 2)))))
           (-> sctx
               (lowering/lower)
               :ws/A
               (ssa/spec-from-ssa)
               :constraints first val)))))

(def lower-refine-to #'lowering/lower-refine-to)

(deftest test-lower-refine-to
  (let [senv (envs/spec-env
              (var-type/to-halite-spec-env
               '{:ws/A
                 {:spec-vars {:an :Integer}
                  :constraints {"a1" (< an 10)}
                  :refines-to {:ws/B {:expr {:$type :ws/B :bn (+ 1 an)}}}}
                 :ws/B
                 {:spec-vars {:bn :Integer}
                  :constraints {"b1" (< 0 bn)}
                  :refines-to {:ws/C {:expr {:$type :ws/C :cn bn}}}}
                 :ws/C
                 {:spec-vars {:cn :Integer}
                  :constraints {"c1" (= 0 (mod cn 2))}}
                 :ws/D
                 {:spec-vars {:dm :Integer, :dn :Integer}
                  :constraints {"d1" (= dm (get (refine-to {:$type :ws/A :an dn} :ws/C) :cn))
                                "d2" (= dn (get (refine-to {:$type :ws/B :bn dn} :ws/B) :bn))
                                "d3" (not= 72 (get {:$type :ws/A :an dn} :an))}}}))
        sctx (ssa/build-spec-ctx senv :ws/D)]
    (is (= '(let [v1 (get {:$type :ws/A, :an dn} :an)
                  v2 (get {:$type :ws/B, :bn (+ 1 v1)} :bn)]
              (and
               (= dm (get {:$type :ws/C, :cn v2} :cn))
               (= dn (get {:$type :ws/B, :bn dn} :bn))
               (not= 72 v1)))
           (-> sctx
               (lower-refine-to)
               :ws/D
               (ssa/spec-from-ssa)
               :constraints first val)))

    (is (= '(let [v1 (get {:$type :ws/A, :an dn} :an)
                  v2 (get {:$type :ws/B, :bn (+ 1 v1)} :bn)]
              (and
               (= dm (get {:$type :ws/C, :cn v2} :cn))
               (= dn (get {:$type :ws/B, :bn dn} :bn))
               (not= 72 v1)))
           (-> sctx
               (lowering/lower)
               :ws/D
               (ssa/spec-from-ssa)
               :constraints first val)))))

(deftest test-lower-refine-to-ignores-unknown-instance-type
  (let [senv (envs/spec-env
              (var-type/to-halite-spec-env
               '{:ws/W {:spec-vars {:wn :Integer}}
                 :ws/A {:refines-to {:ws/W {:expr {:$type :ws/W :wn 1}}}}
                 :ws/B {:refines-to {:ws/W {:expr {:$type :ws/W :wn 2}}}}
                 :ws/C {:spec-vars {:a :ws/A :b :ws/B :p :Boolean}
                        :constraints {"c1" (< 0 (get (refine-to (if p a b) :ws/W) :wn))}}}))]
    (is (= '(< 0 (get (refine-to (if p a b) :ws/W) :wn))
           (-> senv
               (ssa/build-spec-ctx :ws/C)
               lower-refine-to
               :ws/C
               ssa/spec-from-ssa
               :constraints first val)))))

(deftest test-push-refine-to-into-if
  (let [senv (envs/spec-env
              (var-type/to-halite-spec-env
               '{:ws/W {:spec-vars {:wn :Integer}}
                 :ws/A {:refines-to {:ws/W {:expr {:$type :ws/W :wn 1}}}}
                 :ws/B {:refines-to {:ws/W {:expr {:$type :ws/W :wn 2}}}}
                 :ws/C {:spec-vars {:a :ws/A :b :ws/B :p :Boolean}
                        :constraints {"c1" (< 0 (get (refine-to (if p a b) :ws/W) :wn))}}}))]
    (is (= '(< 0 (get (if p (refine-to a :ws/W) (refine-to b :ws/W)) :wn))
           (-> senv
               (ssa/build-spec-ctx :ws/C)
               (rewriting/rewrite-sctx lowering/push-refine-to-into-if)
               :ws/C
               ssa/spec-from-ssa
               :constraints first val)))))

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

(def lower-when-expr #'lowering/lower-when-expr)

(deftest test-lower-when
  (let [ctx (make-empty-ssa-ctx)]
    (are [expr result]
         (= result (rewrite-expr ctx lower-when-expr expr))

      '(when (= 1 2) (+ 3 4)) '(if (= 1 2) (+ 3 4) $no-value))))

(def lower-maybe-comparisons #'lowering/lower-maybe-comparisons)
(def lower-maybe-comparison-expr #'lowering/lower-maybe-comparison-expr)

(deftest test-lower-maybe-comparisons
  (let [sctx {:ws/A {:spec-vars {:u [:Maybe :Integer], :v [:Maybe :Integer], :w [:Maybe :Integer], :x :Integer, :y :Integer}
                     :ssa-graph ssa/empty-ssa-graph}}
        ctx (ssa/make-ssa-ctx sctx (:ws/A sctx))]
    (are [expr lowered]
         (= lowered
            (let [[ssa-graph cid] (ssa/form-to-ssa ctx expr)]
              (-> sctx
                  (update :ws/A assoc :ssa-graph ssa-graph :constraints {"c" cid})
                  (lower-maybe-comparisons)
                  :ws/A
                  (#(binding [ssa/*hide-non-halite-ops* false] (ssa/spec-from-ssa %)))
                  :constraints
                  first val)))

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
                 (update :ws/A assoc :ssa-graph ssa-graph :constraints {"c" cid})
                 (->> (fixpoint lower-maybe-comparisons))
                 :ws/A
                 (#(binding [ssa/*hide-non-halite-ops* false] (ssa/spec-from-ssa %)))
                 :constraints
                 first val))))

    ;; TODO: Add tests of semantics preservation!
    ))

(deftest test-push-comparisons-into-nonprimitive-ifs
  (binding [ssa/*hide-non-halite-ops* false]
    (let [senv (var-type/to-halite-spec-env
                '{:ws/A
                  {:spec-vars {:v [:Maybe :Integer], :w [:Maybe :Integer], :x :Integer, :p :Boolean}}})]
      (are [expr lowered]
           (= lowered
              (-> senv
                  (update-in [:ws/A :constraints] assoc "c" expr)
                  (envs/spec-env)
                  (ssa/build-spec-ctx :ws/A)
                  (rewriting/rewrite-sctx lowering/push-comparison-into-nonprimitive-if-in-expr)
                  :ws/A
                  (ssa/spec-from-ssa)
                  :constraints first val))

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
                   (update-in [:ws/A :constraints] assoc "c" expr)
                   (envs/spec-env)
                   (ssa/build-spec-ctx :ws/A)
                   (->> (fixpoint #(rewriting/rewrite-sctx % lowering/push-comparison-into-nonprimitive-if-in-expr)))
                   :ws/A
                   (ssa/spec-from-ssa)
                   :constraints first val)))))))

(def push-if-value-into-if #'lowering/push-if-value-into-if)

(deftest test-push-if-value-into-if
  (binding [ssa/*hide-non-halite-ops* false]
    (let [senv (var-type/to-halite-spec-env
                '{:ws/A
                  {:spec-vars {:v [:Maybe :Integer], :w [:Maybe :Integer], :x :Integer, :p :Boolean}}
                  :ws/B
                  {}})]

      (are [expr lowered]
           (= lowered
              (-> senv
                  (update-in [:ws/A :constraints] assoc "c" expr)
                  (envs/spec-env)
                  (ssa/build-spec-ctx :ws/A)
                  (push-if-value-into-if)
                  :ws/A
                  (ssa/spec-from-ssa)
                  :constraints first val))

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
                (var-type/to-halite-spec-env
                 '{:ws/A
                   {:spec-vars {:an :Integer, :aw [:Maybe :Integer], :p :Boolean}
                    :constraints {"a1" (= aw (when p an))}}}))
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
                 :constraints first val))))))

(deftest test-lowering-nested-optionals
  (schema.core/without-fn-validation
   (let [senv (envs/spec-env
               (var-type/to-halite-spec-env
                '{:ws/A
                  {:spec-vars {:b1 [:Maybe :ws/B], :b2 [:Maybe :ws/B], :ap :Boolean}
                   :constraints [["a1" (= b1 b2)]
                                 ["a2" (=> ap (if-value b1 true false))]]}
                  :ws/B
                  {:spec-vars {:bx :Integer, :bw [:Maybe :Integer], :bp :Boolean, :c1 :ws/C, :c2 [:Maybe :ws/C]}
                   :constraints [["b1" (= bw (when bp bx))]]}
                  :ws/C
                  {:spec-vars {:cx :Integer
                               :cw [:Maybe :Integer]}}
                  :ws/D
                  {:spec-vars {:dx :Integer}
                   :refines-to {:ws/B {:expr {:$type :ws/B
                                              :bx (+ dx 1)
                                              :bw (when (= 0 (mod dx 2))
                                                    (div dx 2))
                                              :bp false
                                              :c1 {:$type :ws/C :cx dx :cw dx}
                                              :c2 {:$type :ws/C :cx dx :cw 12}}}}}}))]
     ;; TODO: Turn this into a property-based test. It's not useful like this.
     (is (= '{:spec-vars {:ap :Boolean, :b1 [:Maybe [:Instance :ws/B]], :b2 [:Maybe [:Instance :ws/B]]}
              :constraints {"$all"
                            (and
                             (if-value b1
                                       (if-value b2
                                                 (let [v1 (get b2 :c2)
                                                       v2 (get b2 :bw)
                                                       v3 (get b2 :c1)
                                                       v4 (get v3 :cw)
                                                       v5 (get b1 :c1)]
                                                   (and
                                                    (= (get b2 :bp) (get b1 :bp))
                                                    (if-value v2
                                                              (let [v6 (get b1 :bw)] (if-value v6 (= v6 v2) false))
                                                              (let [v6 (get b1 :bw)] (if-value v6 false true)))
                                                    (= (get b2 :bx) (get b1 :bx))
                                                    (and
                                                     (if-value v4
                                                               (let [v6 (get v5 :cw)] (if-value v6 (= v6 v4) false))
                                                               (let [v6 (get v5 :cw)] (if-value v6 false true)))
                                                     (= (get v3 :cx) (get v5 :cx)))
                                                    (if-value v1
                                                              (let [v6 (get b1 :c2)]
                                                                (if-value v6
                                                                          (let [v7 (get v6 :cw)]
                                                                            (and
                                                                             (if-value v7
                                                                                       (let [v8 (get v1 :cw)] (if-value v8 (= v8 v7) false))
                                                                                       (let [v8 (get v1 :cw)] (if-value v8 false true)))
                                                                             (= (get v6 :cx) (get v1 :cx))))
                                                                          false))
                                                              (let [v6 (get b1 :c2)] (if-value v6 false true)))))
                                                 false)
                                       (if-value b2 false true))
                             (=> ap (if-value b1 true false)))}
              :refines-to {}}
            (-> senv
                (ssa/build-spec-ctx :ws/A)
                (lowering/lower)
                :ws/A
                (ssa/spec-from-ssa))))

     (is (= '{:spec-vars {:dx :Integer}
              :constraints {"$all"
                            (let [v1 {:$type :ws/C, :cw dx, :cx dx}
                                  v2 {:$type :ws/C, :cw 12, :cx dx}]
                              (if (= 0 (mod dx 2))
                                (let [v3 (div dx 2)] false)
                                true))},
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

(deftest test-refine-optional
  ;; The 'features' that interact here: valid? and instance literals w/ unassigned variables.
  (rewriting/with-tracing [traces]
    (let [senv (envs/spec-env
                (var-type/to-halite-spec-env
                 '{:my/A {:abstract? true
                          :spec-vars {:a1 [:Maybe :Integer]
                                      :a2 [:Maybe :Integer]}
                          :constraints {"a1_pos" (if-value a1 (> a1 0) true)
                                        "a2_pos" (if-value a2 (> a2 0) true)}}
                   :my/B {:abstract? false
                          :spec-vars {:b [:Maybe :Integer]}
                          :refines-to {:my/A {:expr {:$type :my/A, :a1 b}}}}}))
          sctx (ssa/build-spec-ctx senv :my/B)]
      (is (= '{:abstract? false,
               :constraints {"$all" (if-value b (< 0 b) true)},
               :refines-to {:my/A {:expr {:$type :my/A, :a1 b}}},
               :spec-vars {:b [:Maybe :Integer]}}
             (-> senv
                 (ssa/build-spec-ctx :my/B)
                 (lowering/lower)
                 :my/B
                 (ssa/spec-from-ssa)))))))

(deftest test-eliminate-error-forms
  (let [senv (envs/spec-env
              (var-type/to-halite-spec-env '{:ws/A {:spec-vars {:an :Integer :ap [:Maybe :Boolean]}
                                                    :constraints {"a1" (if (< an 10)
                                                                         (if (< an 1)
                                                                           (error "an is too small")
                                                                           (if-value ap ap false))
                                                                         (= ap (when (< an 20)
                                                                                 (error "not big enough"))))}}}))]
    (is (= '{:spec-vars {:an :Integer :ap [:Maybe :Boolean]}
             :constraints {"$all"
                           (let [v1 (< an 10)]
                             (and
                              (if v1
                                (if-value ap ap false)
                                (= ap $no-value))
                              (not (and (<= 10 an) (< an 20)))
                              (not (and (< an 1) v1))))}
             :refines-to {}}
           (-> senv
               (ssa/build-spec-ctx :ws/A)
               (rewriting/rewrite-sctx lower-when-expr)
               (lowering/eliminate-error-forms)
               :ws/A
               (ssa/spec-from-ssa)))))
  (let [senv (envs/spec-env
              (var-type/to-halite-spec-env
               '{:ws/A {:spec-vars {:an :Integer :ap :Boolean}
                        :constraints {"a1" (if ap
                                             (if (< an 10)
                                               (error "too small")
                                               (not (= 42 (+ 1 (* an (error "too big"))))))
                                             (= an 42))}}}))]
    (is (= '(and (= an 42)
                 (not (and (<= 10 an) ap))
                 (not (and (< an 10) ap)))
           (-> senv
               (ssa/build-spec-ctx :ws/A)
               (rewriting/rewrite-sctx lower-when-expr)
               (lowering/eliminate-error-forms)
               :ws/A
               (ssa/spec-from-ssa)
               :constraints
               first val)))))

(deftest test-eliminate-error-forms-same-message
  ;; TODO: these tests appear to show a bug in the lowering logic
  #_(let [senv (envs/spec-env
                '{:ws/A {:spec-vars {:an :Integer :ap [:Maybe :Boolean]}
                         :constraints {"a1" (if (< an 10)
                                              (if (< an 1)
                                                (error "fail")
                                                (if-value ap ap false))
                                              (= ap (when (< an 20)
                                                      (error "fail"))))}}})]
      (is (= '{:spec-vars {:an :Integer :ap [:Maybe :Boolean]}
               :constraints {"$all"
                             (let [v1 (< an 10)]
                               (and
                                (if v1
                                  (if-value ap ap false)
                                  (= ap $no-value))
                                (not (and (<= 10 an) (< an 20)))
                                (not (and (< an 1) v1))))}
               :refines-to {}}
             (-> senv
                 (ssa/build-spec-ctx :ws/A)
                 (rewriting/rewrite-sctx lower-when-expr)
                 (lowering/eliminate-error-forms)
                 :ws/A
                 (ssa/spec-from-ssa)))))
  #_(let [senv (envs/spec-env
                '{:ws/A {:spec-vars {:an :Integer :ap :Boolean}
                         :constraints {"a1" (if ap
                                              (if (< an 10)
                                                (error "fail")
                                                (not (= 42 (+ 1 (* an (error "fail"))))))
                                              (= an 42))}}})]
      (is (= '(and (= an 42)
                   (not (and (<= 10 an) ap))
                   (not (and (< an 10) ap)))
             (-> senv
                 (ssa/build-spec-ctx :ws/A)
                 (rewriting/rewrite-sctx lower-when-expr)
                 (lowering/eliminate-error-forms)
                 :ws/A
                 (ssa/spec-from-ssa)
                 :constraints
                 first val)))))

(def rewrite-instance-valued-do-child #'lowering/rewrite-instance-valued-do-child)

(deftest test-rewrite-instance-valued-do-child
  (let [senv (var-type/to-halite-spec-env
              '{:ws/A
                {:spec-vars {:ap :Boolean, :ab :ws/B, :an :Integer, :ad :ws/D}}
                :ws/B
                {:spec-vars {:bn [:Maybe :Integer] :bp [:Maybe :Boolean]}}
                :ws/C
                {:spec-vars {:cm :Integer, :cn :Integer}}
                :ws/D
                {:spec-vars {:dn :Integer :dc :ws/C}}})]
    (are [in out]
         (= out
            (let [sctx (-> senv
                           (update-in [:ws/A :constraints] assoc "a1" (list '$do! in 'ap))
                           envs/spec-env
                           (ssa/build-spec-ctx :ws/A))
                  ctx (ssa/make-ssa-ctx sctx (:ws/A sctx))
                  do-id (->> (get-in sctx [:ws/A :constraints])
                             first
                             val)
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
  (let [senv (var-type/to-halite-spec-env '{:ws/A
                                            {:spec-vars {:aw [:Maybe :Integer] :ap :Boolean :an :Integer}}})]
    (are [in out]
         (= out
            (let [sctx (-> senv
                           (update-in [:ws/A :constraints] assoc "a1" (list '$do! in 'true))
                           envs/spec-env
                           (ssa/build-spec-ctx :ws/A))
                  ctx (ssa/make-ssa-ctx sctx (:ws/A sctx))
                  do-id (->> (get-in sctx [:ws/A :constraints])
                             first
                             val)
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
              (var-type/to-halite-spec-env
               '{:ws/A
                 {:spec-vars {:ap :Boolean, :ab :ws/B, :an :Integer, :ad :ws/D}
                  :constraints {"a1" (let [b (if ap
                                               {:$type :ws/B :bn (+ 1 an)}
                                               {:$type :ws/B :bn (+ 2 an)})]
                                       true)}}
                 :ws/B
                 {:spec-vars {:bn [:Maybe :Integer] :bp [:Maybe :Boolean]}}
                 :ws/C
                 {:spec-vars {:cm :Integer, :cn :Integer}}
                 :ws/D
                 {:spec-vars {:dn :Integer :dc :ws/C}}}))
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
               :constraints first val)))))

(defonce ^:dynamic *results* (atom nil))
(defonce ^:dynamic *trace* (atom nil))

(deftest test-lowering-optionality
  (schema.core/without-fn-validation
   (binding [ssa/*hide-non-halite-ops* true]
     (let [senv (envs/spec-env
                 (var-type/to-halite-spec-env
                  '{:ws/A
                    {:spec-vars {:b1 [:Maybe :ws/B], :b2 [:Maybe :ws/B], :aw [:Maybe :Integer], :x :Integer, :p :Boolean}
                     :constraints {"a1" (not= (if p b1 b2)
                                              (if-value aw
                                                        {:$type :ws/B :bw aw :bx (+ aw 1)}
                                                        (get {:$type :ws/C :b3 b1 :cw x} :b3)))}}
                    :ws/B
                    {:spec-vars {:bv [:Maybe :Integer], :bw [:Maybe :Integer], :bx :Integer}}
                    :ws/C
                    {:spec-vars {:b3 [:Maybe :ws/B], :cw :Integer}
                     :constraints {"c1" (= (< 0 cw) (if-value b3 true false))
                                   "c2" (if-value b3
                                                  (let [bw (get b3 :bw)]
                                                    (if-value bw
                                                              (< cw bw)
                                                              false))
                                                  true)}}}))
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
                           (clojure.pprint/pprint (ssa/spec-from-ssa (get sctx spec-id)))))]
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
                  {:spec-vars {:a :ws/A}}
                  :ws/Y
                  {:spec-vars {:b :ws/B}}
                  :ws/C
                  {:spec-vars {:x :ws/X, :y :ws/Y}
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
