;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.lowering
  "Re-express halite specs in a minimal subset of halite, by compiling higher-level
  features down into lower-level features."
  (:require [clojure.set :as set]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.transpile.ssa :as ssa
             :refer [DerivationName Derivations SpecInfo SpecCtx make-ssa-ctx]]
            [jibe.halite.transpile.rewriting :refer [rewrite-sctx]]
            [jibe.halite.transpile.simplify :refer [simplify]]
            [jibe.halite.transpile.util :refer [fixpoint mk-junct]]
            [schema.core :as s]
            [weavejester.dependency :as dep]))



;;;;;;;;; Instance Comparison Lowering ;;;;;;;;

(s/defn ^:private lower-instance-comparison-expr
  [{:keys [dgraph senv] :as ctx} id [form type]]
  (when (and (seq? form) (#{'= 'not=} (first form)))
    (let [comparison-op (first form)
          logical-op (if (= comparison-op '=) 'and 'or)
          arg-ids (rest form)
          arg-types (set (map (comp second dgraph) arg-ids))]
      (when (every? halite-types/spec-type? arg-types)
        (if (not= 1 (count arg-types))
          (= comparison-op 'not=)
          (let [arg-type (first arg-types)
                var-kws (->> arg-type (halite-types/spec-id) (halite-envs/lookup-spec senv) :spec-vars keys sort)]
            (->> var-kws
                 (map (fn [var-kw]
                        (apply list comparison-op
                               (map #(list 'get %1 var-kw) arg-ids))))
                 (mk-junct logical-op))))))))

(s/defn ^:private lower-instance-comparisons :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx lower-instance-comparison-expr))

;;;;;;;;; Push gets inside instance-valued ifs ;;;;;;;;;;;

(s/defn ^:private push-gets-into-ifs-expr
  [{:keys [dgraph] :as ctx} :- ssa/SSACtx, id, [form htype]]
  (when (and (seq? form) (= 'get (first form)))
    (let [[_get arg-id var-kw] form
          [subform htype] (ssa/deref-id dgraph (second form))]
      (when (and (seq? subform) (= 'if (first subform)) (halite-types/spec-type? htype))
        (let [[_if pred-id then-id else-id] subform]
          (list 'if pred-id (list 'get then-id var-kw) (list 'get else-id var-kw)))))))

(s/defn ^:private push-gets-into-ifs :- SpecCtx
  [sctx :- SpecCtx]
  (rewrite-sctx sctx push-gets-into-ifs-expr))

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
      :else (throw (ex-info "BUG! Cannot compute validity-guard" {:dgraph dgraph :id id :deriv deriv})))))

(s/defn ^:private lower-valid?-in-spec :- SpecInfo
  "We can trivially lower (valid? <expr>) forms as <(validity-guard expr)>."
  [sctx :- SpecCtx, {:keys [derivations] :as spec-info} :- SpecInfo]
  (let [ctx (make-ssa-ctx sctx spec-info)]
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

;;;;;;;;;; Lower refine-to ;;;;;;;;;;;;;;;

(defn- refn-paths*
  [refn-graph curr-id target-id]
  (if (= curr-id target-id)
    [[target-id]]
    (let [paths (mapcat #(refn-paths* refn-graph % target-id) (refn-graph curr-id))]
      (mapv (partial cons curr-id) paths))))

(s/defn ^:private refn-paths :- [[halite-types/NamespacedKeyword]]
  [sctx :- SpecCtx, from-spec-id :- halite-types/NamespacedKeyword, to-spec-id :- halite-types/NamespacedKeyword]
  ;; NOTE! This function *assumes* the refinement graph is acyclic, and will not terminate otherwise.
  (let [refn-graph (-> (->> sctx
                            (mapcat (fn [[from-id {:keys [refines-to]}]]
                                      (for [to-id (keys refines-to)]
                                        [from-id to-id])))
                            (group-by first))
                       (update-vals (partial mapv second)))]
    (refn-paths* refn-graph from-spec-id to-spec-id)))

(s/defn ^:private lower-refine-to-expr :- ssa/DerivResult
  [{:keys [dgraph senv] :as ctx} :- ssa/SSACtx
   expr-id :- DerivationName
   from-spec-id :- halite-types/NamespacedKeyword
   path :- [halite-types/NamespacedKeyword]]
  (if (empty? path)
    [dgraph expr-id]
    (let [{:keys [spec-vars refines-to]} (halite-envs/lookup-spec senv from-spec-id)
          to-spec-id (first path)
          [dgraph new-expr-id] (ssa/dup-node dgraph expr-id)
          bindings (vec (mapcat (fn [var-kw] [(symbol var-kw) (list 'get new-expr-id var-kw)]) (keys spec-vars)))
          refn-expr (-> refines-to to-spec-id :expr)
          new-form (list 'let bindings refn-expr)
          [dgraph id] (ssa/form-to-ssa (assoc ctx :dgraph dgraph) expr-id new-form)]
      (recur (assoc ctx :dgraph dgraph) id to-spec-id (rest path)))))

(s/defn ^:private lower-refine-to-in-spec :- SpecInfo
  [sctx :- SpecCtx, {:keys [derivations] :as spec-info} :- SpecInfo]
  (let [ctx (make-ssa-ctx sctx spec-info)]
    (->> derivations
         (filter
          (fn [[id [form]]] (and (seq? form) (= 'refine-to (first form)))))
         (reduce
          (fn [dgraph [id [[_refine-to expr-id to-spec-id]] :as deriv]]
            (let [[_ htype] (ssa/deref-id dgraph expr-id)
                  from-spec-id (halite-types/spec-id htype)]
              (if (= from-spec-id to-spec-id)
                (ssa/rewrite-node dgraph id expr-id)
                (let [paths (refn-paths sctx from-spec-id to-spec-id)
                      npaths (count paths)]
                  (condp = npaths
                    0 (throw (ex-info (format "BUG! No refinement path from '%s' to '%s'" from-spec-id to-spec-id)
                                      {:dgraph derivations :id id :deriv deriv}))
                    1 (let [[dgraph new-id] (lower-refine-to-expr (assoc ctx :dgraph dgraph) expr-id from-spec-id (drop 1 (first paths)))]
                        (ssa/rewrite-node dgraph id new-id))
                    (throw (ex-info (format "BUG! Multiple refinement paths from '%s' to '%s'" from-spec-id to-spec-id)
                                    {:dgraph derivations :id id :deriv deriv :paths paths})))))))
          derivations)
         (assoc spec-info :derivations))))

(s/defn ^:private lower-refine-to :- SpecCtx
  [sctx :- SpecCtx]
  (update-vals sctx (partial lower-refine-to-in-spec sctx)))

;;;;;;;;;; Lower Refinement Constraints ;;;;;;;;;;;;;;;

(s/defn ^:private lower-refinement-constraints-in-spec :- SpecInfo
  [sctx :- SpecCtx, {:keys [refines-to derivations] :as spec-info} :- SpecInfo]
  (let [ctx (make-ssa-ctx sctx spec-info)]
    (->> refines-to
         (reduce
          (fn [{:keys [derivations] :as spec-info} [target-id {:keys [expr inverted?]}]]
            (when inverted?
              (throw (ex-info "BUG! Lowering inverted refinements not yet supported" {:spec-info spec-info})))
            (let [[dgraph id] (ssa/form-to-ssa (assoc ctx :dgraph derivations) (list 'valid? expr))]
              (-> spec-info
                  (assoc :derivations dgraph)
                  (update :constraints conj [(str target-id) id]))))
          spec-info))))

(s/defn ^:private lower-refinement-constraints :- SpecCtx
  [sctx :- SpecCtx]
  (update-vals sctx (partial lower-refinement-constraints-in-spec sctx)))

;;;;;;;;;; Combine semantics-preserving passes ;;;;;;;;;;;;;

(s/defn lower :- SpecCtx
  "Return a semantically equivalent spec context containing specs that have been reduced to
  a minimal subset of halite."
  [sctx :- SpecCtx]
  (->> sctx
       (fixpoint lower-instance-comparisons)
       (fixpoint push-gets-into-ifs)
       (lower-refine-to)
       (lower-refinement-constraints)
       (lower-valid?)
       (simplify)))

;;;;;;;;;; Semantics-modifying passes ;;;;;;;;;;;;;;;;

(s/defn ^:private eliminate-runtime-constraint-violations-in-spec :- SpecInfo
  [sctx :- SpecCtx, {:keys [derivations constraints] :as spec-info} :- SpecInfo]
  (let [ctx (make-ssa-ctx sctx spec-info)]
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
  (rewrite-in-dependency-order sctx deps-via-instance-literal eliminate-runtime-constraint-violations-in-spec))

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

(s/defn cancel-get-of-instance-literal :- SpecCtx
  "Replace (get {... :k <subexpr>} :k) with <subexpr>. Not semantics preserving, in that
  the possible runtime constraint violations of the instance literal are eliminated."
  [sctx :- SpecCtx]
  (update-vals sctx cancel-get-of-instance-literal-in-spec))
