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
            [jibe.halite.transpile.util :refer [mk-junct]]
            [schema.core :as s]
            [weavejester.dependency :as dep]))

;;;;;;;; Fixpoint ;;;;;;;;;;;;

;; Some passes need to be run repeatedly, until there is no change.

(defn- fixpoint
  [f input]
  (loop [x input]
    (let [x' (f x)]
      (cond-> x' (not= x' x) recur))))

;;;;;;;; Guards ;;;;;;;;;;;;;;

(s/defschema ^:private Guards
  "For every derivation, a representation in disjunctive normal form of what must be true for that
  derivation to get 'evaluated'."
  {DerivationName #{#{DerivationName}}})

(s/defn ^:private update-guards :- #{#{DerivationName}}
  [current :- #{#{DerivationName}}, guard :- #{DerivationName}]
  (as-> current guards
    ;; Any existing conjuncts that are supersets of guard may be eliminated.
    (->> guards (remove (partial set/subset? guard)) set)

    ;; If any existing conjunct is a subset of guard, guard may be ignored.
    (cond-> guards
      (not (some #(set/subset? % guard) guards)) (conj guard))))

(s/defn ^:private compute-guards* :- Guards
  [dgraph :- Derivations, current :- #{DerivationName}, result :- Guards, id :- DerivationName]
  (let [[form htype] (dgraph id)
        result (update result id update-guards current)]
    (cond
      (or (integer? form) (boolean? form) (symbol? form)) result
      (seq? form) (let [[op & args] form]
                    (condp = op
                      'get (compute-guards* dgraph current result (first args))
                      'if (let [[pred-id then-id else-id] args
                                not-pred-id (ssa/negated dgraph pred-id)]
                            (as-> result result
                              (compute-guards* dgraph current result pred-id)
                              (compute-guards* dgraph (conj current pred-id) result then-id)
                              (compute-guards* dgraph (conj current not-pred-id) result else-id)))
                      (reduce (partial compute-guards* dgraph current) result args)))
      (map? form) (->> (dissoc form :$type) vals (reduce (partial compute-guards* dgraph current) result))
      :else (throw (ex-info "BUG! Could not compute guards for form"
                            {:id id :form form :dgraph dgraph :current current :result result})))))

(s/defn ^:private simplify-guards :- #{#{DerivationName}}
  [dgraph :- Derivations, guards :- #{#{DerivationName}}]
  ;; guards is in disjunctive normal form... if a conjunct and
  ;; its negation are both in guards, then the whole expression collapses to 'true'
  ;; This is just a heuristic intended primarily to catch when an expression shows up
  ;; in both branches of an if. This problem is in general co-NP-hard.
  ;; https://en.wikipedia.org/wiki/Disjunctive_normal_form
  (let [negated-clauses (->> guards (map #(->> % (map (partial ssa/negated dgraph)) set)))]
    (if (some (fn [negated-terms] (every? #(contains? guards #{%}) negated-terms)) negated-clauses)
      #{#{}} ; true
      guards)))

(s/defn ^:private compute-guards :- Guards
  [{:keys [derivations constraints] :as spec-info} :- SpecInfo]
  (-> (reduce
       (partial compute-guards* derivations #{})
       (zipmap (keys derivations) (repeat #{}))
       (map second constraints))
      (update-vals (partial simplify-guards derivations))))

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

;;;;;;;;; Instance Literal Lowering ;;;;;;;;;;;

(s/defn ^:private lower-implicit-constraints-in-spec :- SpecInfo
  [sctx :- SpecCtx, spec-info :- SpecInfo]
  (let [guards (compute-guards spec-info)
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
  (let [dg (reduce
            (fn [dg [spec-id spec]]
              (reduce
               (fn [dg [id [form htype]]]
                 (cond-> dg
                   (map? form) (dep/depend spec-id (:$type form))))
               dg
               (:derivations spec)))
            ;; ensure that everything is in the dependency graph, depending on :nothing
            (reduce #(dep/depend %1 %2 :nothing) (dep/graph) (keys sctx))
            sctx)]
    (reduce
     (fn [sctx' spec-id]
       (assoc sctx' spec-id (lower-implicit-constraints-in-spec sctx' (sctx spec-id))))
     ;; We start with the original spec context, but we process specs in an order such that
     ;; any instance literal getting its implicit constraints inlined is of a spec that has already
     ;; been processed.
     sctx
     (->> dg (dep/topo-sort) (remove #(= :nothing %))))))

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
       (lower-implicit-constraints)
       (fixpoint cancel-get-of-instance-literal)))
