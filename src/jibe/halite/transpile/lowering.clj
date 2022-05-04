;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.lowering
  "Re-express halite specs in a minimal subset of halite, by compiling higher-level
  features down into lower-level features."
  (:require [clojure.set :as set]
            [jibe.halite.envs :as halite-envs]
            [jibe.halite.types :as halite-types]
            [jibe.halite.transpile.ssa :as ssa
             :refer [DerivationName Derivations SpecInfo SpecCtx]]
            [jibe.halite.transpile.util :refer [fixpoint]]
            [jibe.halite.transpile.util :refer [mk-junct]]
            [schema.core :as s]
            [weavejester.dependency :as dep]))

;;;;;;;;; Instance Comparison Lowering ;;;;;;;;

;; Assumes abstract expressions and optional vars have been lowered.

(s/defn ^:private lower-instance-comparisons-in-spec :- SpecInfo
  [sctx :- SpecCtx, {:keys [derivations] :as spec-info} :- SpecInfo]
  (let [senv (ssa/as-spec-env sctx)
        tenv (halite-envs/type-env-from-spec senv (dissoc spec-info :derivations))
        ctx {:senv senv :tenv tenv :env {} :dgraph derivations}]
    (->> derivations
         (reduce
          (fn [dgraph [id [form type]]]
            (if (and (seq? form) (#{'= 'not=} (first form)))
              (let [comparison-op (first form)
                    logical-op (if (= comparison-op '=) 'and 'or)
                    arg-ids (rest form)
                    arg-types (set (map (comp second dgraph) arg-ids))]
                (if (some halite-types/spec-type? arg-types)
                  (first
                   (ssa/form-to-ssa
                    (assoc ctx :dgraph dgraph)
                    id
                    (if (not= 1 (count arg-types))
                      (= comparison-op 'not=)
                      (let [arg-type (first arg-types)
                            var-kws (-> arg-type sctx :spec-vars keys sort)]
                        (->> var-kws
                             (map (fn [var-kw]
                                    (apply list comparison-op
                                           (map #(list 'get %1 var-kw) arg-ids))))
                             (mk-junct logical-op))))))
                  dgraph))
              dgraph))
          derivations)
         (assoc spec-info :derivations))))

(s/defn ^:private lower-instance-comparisons :- SpecCtx
  [sctx :- SpecCtx]
  (update-vals sctx (partial lower-instance-comparisons-in-spec sctx)))

;;;;;;;;; Push gets inside instance-valued ifs ;;;;;;;;;;;

(s/defn ^:private push-gets-into-ifs-in-spec :- SpecInfo
  [sctx :- SpecCtx, {:keys [derivations] :as spec-info} :- SpecInfo]
  (let [senv (ssa/as-spec-env sctx)
        tenv (halite-envs/type-env-from-spec senv (dissoc spec-info :derivations))
        ctx {:senv senv :tenv tenv :env {} :dgraph derivations}]
    (->
     (->> derivations
          (filter (fn [[id [form htype]]]
                    (when (and (seq? form) (= 'get (first form)))
                      (let [[subform htype] (derivations (second form))]
                        (and (seq? subform)
                             (= 'if (first subform))
                             (halite-types/spec-type? htype))))))
          (reduce
           (fn [dgraph [get-id [[_get subexp-id var-kw] htype]]]
             (let [[[_if pred-id then-id else-id]] (derivations subexp-id)]
               (first
                (ssa/form-to-ssa
                 (assoc ctx :dgraph dgraph)
                 get-id
                 (list 'if pred-id
                       (list 'get then-id var-kw)
                       (list 'get else-id var-kw))))))
           derivations)
          (assoc spec-info :derivations))
     (ssa/prune-derivations false))))

(s/defn ^:private push-gets-into-ifs :- SpecCtx
  [sctx :- SpecCtx]
  (update-vals sctx (partial push-gets-into-ifs-in-spec sctx)))

;;;;;;;;; Lower valid? ;;;;;;;;;;;;;;;;;

(s/defn ^:private rewrite-in-dependency-order :- SpecCtx
  [sctx :- SpecCtx, deps-fn, rewrite-fn]
  (as-> (dep/graph) dg
    ;; ensure that everything is in the dependency graph, depending on :nothing
    (reduce #(dep/depend %1 %2 :nothing) dg (keys sctx))
    ;; add the deps for each spec
    (reduce
     (fn [dg [spec-id spec]]
       (reduce #(dep/depend %1 spec-id %2) dg (deps-fn spec)))
     dg
     sctx)
    ;; rewrite in the correct order
    (->> dg
         (dep/topo-sort)
         (remove #(= :nothing %))
         (reduce
          (fn [sctx spec-id]
            (->> spec-id (sctx) (rewrite-fn sctx) (assoc sctx spec-id)))
          sctx))))

(s/defn ^:private deps-via-instance-literal :- #{halite-types/NamespacedKeyword}
  [{:keys [derivations] :as spec-info} :- SpecInfo]
  (->> derivations
       vals
       (map (fn [[form htype]] (when (map? form) (:$type form))))
       (remove nil?)
       (set)))

(declare validity-guard)

(s/defn ^:private validity-guard-inst :- ssa/DerivResult
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, [ssa-form htype] :- ssa/Derivation]
  (let [{:keys [derivations constraints] :as spec-info} (sctx (:$type ssa-form))
        inst-entries (->> (dissoc ssa-form :$type) (sort-by first))
        [dgraph pred-clauses] (reduce
                               (fn [[dgraph pred-clauses] [var-kw var-expr-id]]
                                 (let [[dgraph clause-id] (validity-guard sctx (assoc ctx :dgraph dgraph) var-expr-id)]
                                   [dgraph (conj pred-clauses clause-id)]))
                               [dgraph []]
                               inst-entries)
        [dgraph bindings] (reduce
                           (fn [[dgraph bindings] [var-kw var-expr-id]]
                             [dgraph (conj bindings (symbol var-kw) var-expr-id)])
                           [dgraph []]
                           inst-entries)
        scope (->> spec-info :spec-vars keys (map symbol) set)]
    (ssa/form-to-ssa
     (assoc ctx :dgraph dgraph)
     (list 'if
           (mk-junct 'and pred-clauses)
           (list 'let (vec bindings)
                 (mk-junct 'and
                           (map (fn [[cname id]]
                                  (ssa/form-from-ssa scope derivations id))
                                constraints)))
           false))))

(s/defn ^:private validity-guard-if :- ssa/DerivResult
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, [[_if pred-id then-id else-id] htype] :- ssa/Derivation]
  (let [[dgraph pred-guard-id] (validity-guard sctx ctx pred-id)
        [dgraph then-guard-id] (validity-guard sctx (assoc ctx :dgraph dgraph) then-id)
        [dgraph else-guard-id] (validity-guard sctx (assoc ctx :dgraph dgraph) else-id)]
    (ssa/form-to-ssa
     (assoc ctx :dgraph dgraph)
     (list 'if pred-guard-id
           (list 'if pred-id then-guard-id else-guard-id)
           false))))

(s/defn ^:private validity-guard-app :- ssa/DerivResult
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, [[op & args] htype] :- ssa/Derivation]
  (let [[dgraph guard-ids] (reduce
                            (fn [[dgraph guard-ids] arg-id]
                              (let [[dgraph guard-id] (validity-guard sctx (assoc ctx :dgraph dgraph) arg-id)]
                                [dgraph (conj guard-ids guard-id)]))
                            [dgraph []]
                            args)]
    (ssa/form-to-ssa
     (assoc ctx :dgraph dgraph)
     (mk-junct 'and guard-ids))))

(s/defn validity-guard :- ssa/DerivResult
  "Given an expression <expr>, the function validity-guard computes an expression that
  * evaluates to true iff <expr> evaluates to a value
  * evaluates to false iff evaluating <expr> results in the evaluation of an invalid instance
  * evaluates to a runtime error otherwise

  (validity-guard <int>) => true
  (validity-guard <boolean>) => true
  (validity-guard <spec-var>) => true
  (validity-guard (if <pred> <then> <else>))
  => (if <(validity-guard pred)>
       (if <pred> <(validity-guard then)> <(validity-guard else)>)
       false)
  (validity-guard (when <pred> <then>)
  => (if <(validity-guard pred)>
       (if <pred> <(validity-guard then)> true)
       false)
  (validity-guard (get <expr> <var-kw>) => <(validity-guard expr)>
  (validity-guard (valid? <expr>)) => <(validity-guard expr)>
  (validity-guard (<op> <...expr_i>)) => (and <...(validity-guard expr_i)>)
  (validity-guard {<...kw_i expr_i>})
  => (if (and <...(validity-guard expr_i)>)
       (let [<...kw_i expr_i>]
         <inlined constraints>)
       false)"
  [sctx :- SpecCtx, {:keys [dgraph] :as ctx} :- ssa/SSACtx, id :- DerivationName]
  (let [[ssa-form htype :as deriv] (ssa/deref-id dgraph id)]
    (cond
      (int? ssa-form) (ssa/form-to-ssa ctx true)
      (boolean? ssa-form) (ssa/form-to-ssa ctx true)
      (symbol? ssa-form) (ssa/form-to-ssa ctx true)
      (map? ssa-form) (validity-guard-inst sctx ctx deriv)
      (seq? ssa-form) (let [[op & args] ssa-form]
                        (condp = op
                          'if (validity-guard-if sctx ctx deriv)
                          'get (validity-guard sctx ctx (first args))
                          'valid? (validity-guard sctx ctx (first args))
                          (validity-guard-app sctx ctx deriv)))
      :else (throw (ex-info "BUG! Cannot compute validity-guard" {:dgraph dgraph :id id :deriv deriv}))
      )))

(s/defn ^:private lower-valid?-in-spec :- SpecInfo
 "We can trivially lower (valid? <expr>) forms as <(validity-guard expr)>."
  [sctx :- SpecCtx, {:keys [derivations] :as spec-info} :- SpecInfo]
  (let [senv (ssa/as-spec-env sctx)
        tenv (halite-envs/type-env-from-spec senv (dissoc spec-info :derivations))
        ctx {:senv senv :tenv tenv :env {} :dgraph derivations}]
    (->> derivations
         (filter
          (fn [[id [form htype]]]
            (and (seq? form) (= 'valid? (first form)))))
         (reduce
          (fn [dgraph [id [[_valid? expr-id] htype]]]
            (let [[dgraph new-id] (validity-guard sctx (assoc ctx :dgraph dgraph) expr-id)
                  deriv (ssa/deref-id dgraph new-id)]
              (assoc dgraph id (assoc deriv 0 new-id))))
          derivations)
         (assoc spec-info :derivations))))

(s/defn ^:private lower-valid? :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-in-dependency-order sctx deps-via-instance-literal lower-valid?-in-spec))

;;;;;;;;; Instance Literal Lowering ;;;;;;;;;;;

(s/defn ^:private lower-implicit-constraints-in-spec :- SpecInfo
  [sctx :- SpecCtx, spec-info :- SpecInfo]
  (let [guards (ssa/compute-guards (:derivations spec-info) (->> spec-info :constraints (map second) set))
        inst-literals (->> spec-info
                           :derivations
                           (filter (fn [[id [form htype]]] (map? form))))
        senv (ssa/as-spec-env sctx)
        tenv (halite-envs/type-env-from-spec senv (dissoc spec-info :derivations))]
    (reduce
     (fn [{:keys [derivations] :as spec-info} [id [inst-literal htype]]]
       (let [spec-id (:$type inst-literal)
             guard-form (->> id guards (map #(mk-junct 'and (sort %))) (mk-junct 'or))
             constraints (->> spec-id sctx ssa/spec-from-ssa :constraints (map second))
             constraint-expr (list 'let (vec (mapcat (fn [[var-kw id]] [(symbol var-kw) id]) (dissoc inst-literal :$type)))
                                   (mk-junct 'and constraints))
             constraint-expr (if (not= true guard-form)
                               (list 'if guard-form constraint-expr true)
                               constraint-expr)
             [derivations id] (ssa/form-to-ssa {:senv senv :tenv tenv :env {} :dgraph derivations} constraint-expr)]
         (-> spec-info
             (assoc :derivations derivations)
             (update :constraints conj ["$inst" id]))))
     spec-info
     inst-literals)))

(s/defn ^:private lower-implicit-constraints :- SpecCtx
  "Make constraints implied by the presence of instance literals explicit.
  Specs are lowered in an order that respects a spec dependency graph where spec S has an arc to T
  iff S has an instance literal of type T. If a cycle is detected, the phase will throw."
  [sctx :- SpecCtx]
  (rewrite-in-dependency-order sctx deps-via-instance-literal lower-implicit-constraints-in-spec))

(s/defn ^:private cancel-get-of-instance-literal-in-spec :- SpecInfo
  "Replace (get {... :k <subexpr>} :k) with <subexpr>."
  [{:keys [derivations] :as spec-info} :- SpecInfo]
  (->
   (->> spec-info
        :derivations
        (filter (fn [[id [form htype]]]
                  (if (and (seq? form) (= 'get (first form)))
                    (let [[subform] (ssa/deref-id derivations (second form))]
                      (map? subform)))))
        (reduce
         (fn [dgraph [id [[_get subid var-kw] htype]]]
           (let [[inst-form] (ssa/deref-id dgraph subid)
                 field-node (ssa/deref-id dgraph (get inst-form var-kw))]
             (assoc dgraph id field-node)))
         derivations)
        (assoc spec-info :derivations))
   (ssa/prune-derivations false)))

(s/defn ^:private cancel-get-of-instance-literal :- SpecCtx
  [sctx :- SpecCtx]
  (update-vals sctx cancel-get-of-instance-literal-in-spec))

(s/defn lower :- SpecCtx
  "Return a semantically equivalent spec context containing specs that have been reduced to
  a minimal subset of halite."
  [sctx :- SpecCtx]
  (->> sctx
       (fixpoint lower-instance-comparisons)
       (fixpoint push-gets-into-ifs)
       (lower-valid?)
       (lower-implicit-constraints) ; TODO: remove me! I'm not semantics-preserving!
       (fixpoint cancel-get-of-instance-literal)))

(s/defn ^:private eliminate-runtime-constraint-violations-in-spec :- SpecInfo
  [sctx :- SpecCtx, {:keys [derivations constraints] :as spec-info} :- SpecInfo]
  (let [senv (ssa/as-spec-env sctx)
        ctx {:senv senv
             :tenv (halite-envs/type-env-from-spec senv (dissoc spec-info :derivations))
             :env {}
             :dgraph derivations}]
    (->> constraints
         (reduce
          (fn [acc [cname cid]]
            (let [[dgraph guard-id] (validity-guard sctx (assoc ctx :dgraph (:derivations acc)) cid)
                  [dgraph id] (->> (list 'if guard-id cid false)
                                   (ssa/form-to-ssa (assoc ctx :dgraph dgraph)))]
              (-> acc
                  (assoc :derivations dgraph)
                  (update :constraints conj [cname id]))))
          {:derivations derivations
           :constraints []})
         (merge spec-info))))

(s/defn eliminate-runtime-constraint-violations :- SpecCtx
  "Rewrite the constraints of every spec to eliminate the possibility of runtime constraint
  violations (but NOT runtime errors in general!). This is not a semantics-preserving operation.

  Every constraint expression <expr> is rewritten as (if <(validity-guard expr)> <expr> false)."
  [sctx :- SpecCtx]
  (rewrite-in-dependency-order
   sctx deps-via-instance-literal
   eliminate-runtime-constraint-violations-in-spec))


