;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-composition
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.types :as halite-types]
            [com.viasat.halite.propagate.prop-strings :as prop-strings]
            [com.viasat.halite.transpile.ssa :as ssa :refer [SpecCtx SpecInfo]]
            [com.viasat.halite.transpile.lowering :as lowering]
            [com.viasat.halite.transpile.util :refer [fixpoint mk-junct]]
            [com.viasat.halite.transpile.simplify :as simplify :refer [simplify-redundant-value! simplify-statically-known-value?]]
            [com.viasat.halite.transpile.rewriting :as rewriting]
            [loom.graph :as loom-graph]
            [loom.derived :as loom-derived]
            [schema.core :as s]
            [com.viasat.halite.choco-clj-opt :as choco-clj]))

(declare ConcreteBound)

(s/defschema RefinementBound
  {halite-types/NamespacedKeyword
   {halite-types/BareKeyword (s/recursive #'ConcreteBound)}})

(s/defschema ConcreteSpecBound
  {:$type (s/cond-pre [(s/one (s/enum :Maybe) :maybe) (s/one halite-types/NamespacedKeyword :type)]
                      halite-types/NamespacedKeyword)
   (s/optional-key :$refines-to) RefinementBound
   halite-types/BareKeyword (s/recursive #'ConcreteBound)})

(s/defschema AtomBound
  prop-strings/AtomBound)

(s/defschema ConcreteBound
  (s/conditional
   :$type ConcreteSpecBound
   :else AtomBound))

;;;;;;;;; Bound Spec-ification ;;;;;;;;;;;;;;;;

(s/defschema ^:private PrimitiveType (s/enum "Boolean" "Integer"))
(s/defschema ^:private PrimitiveMaybeType [(s/one (s/enum :Maybe) :maybe) (s/one PrimitiveType :inner)])

(s/defschema ^:private FlattenedVar
  [(s/one halite-types/BareKeyword :var-kw)
   (s/one (s/cond-pre PrimitiveMaybeType PrimitiveType) :type)])

(declare FlattenedRefinementMap)

;; A FlattenedVar represents a mapping from a ConcerteSpecBound with vars that are
;; unique within that individual spec instance to choco var names that are
;; unique in the whole composed choco spec, to the depth of composition implied
;; by the given ConcreteSpecBound. FVs form a tree of the same shape as a ConcreteSpecBound,
;; with each ConcreteSpecBound's :$type the same as its corresponding FV's ::spec-id,
;; and every var of that Spec appearing as a key in the FV.
(s/defschema ^:private FlattenedVars
  {::mandatory #{halite-types/BareKeyword}
   ::spec-id halite-types/NamespacedKeyword
   (s/optional-key ::refines-to) (s/recursive #'FlattenedRefinementMap)
   halite-types/BareKeyword (s/cond-pre FlattenedVar (s/recursive #'FlattenedVars))})

;; A FlattenedRefinement is like a FlattenedVar, but does not support a
;; ::refines-to map because the right place to put such a flattening would be in
;; the FlattenedVar that contains this FlattenedRefinement.
(s/defschema ^:private FlattenedRefinement
  {::mandatory #{halite-types/BareKeyword}
   halite-types/BareKeyword (s/cond-pre FlattenedVar FlattenedVars)})

(s/defschema ^:private FlattenedRefinementMap
  {halite-types/NamespacedKeyword FlattenedRefinement})

(defn- primitive-maybe-type?
  [htype]
  (or (#{:Integer :Boolean :String} htype)
      (and (halite-types/maybe-type? htype) (vector? htype)
           (#{:Integer :Boolean :String} (second htype)))))

(defn- spec-maybe-type?
  [htype]
  (or (halite-types/spec-type? htype)
      (halite-types/spec-type? (halite-types/no-maybe htype))))

(defn- unwrap-maybe [htype]
  (cond-> htype
    (and (vector? htype) (= :Maybe (first htype))) second))

(defn refined-var-name [prefix spec-id]
  (str prefix ">" (namespace spec-id) "$" (name spec-id) "|"))

(s/defn ^:private flatten-vars :- FlattenedVars
  ([sctx :- ssa/SpecCtx, spec-bound :- ConcreteSpecBound]
   (flatten-vars sctx [] "" false spec-bound))
  ([sctx :- ssa/SpecCtx
    parent-spec-ids :- [halite-types/NamespacedKeyword]
    prefix :- s/Str
    already-optional? :- s/Bool
    spec-bound :- ConcreteSpecBound]
   (let [spec-id (->> spec-bound :$type unwrap-maybe)
         spec-refinements #(-> % sctx :refines-to)
         senv (ssa/as-spec-env sctx)]
     (->
      (reduce
       (fn [vars [var-kw vtype]]
         (let [htype (envs/halite-type-from-var-type senv vtype)]
           (cond
             (primitive-maybe-type? htype)
             (let [actually-mandatory? (and already-optional? (not (halite-types/maybe-type? htype)))
                   prefixed-var-kw (keyword (str prefix (name var-kw)))]
               (-> vars
                   (assoc var-kw [prefixed-var-kw
                                  (cond->> vtype
                                    actually-mandatory? (vector :Maybe))])
                   (cond->
                    actually-mandatory? (update ::mandatory conj prefixed-var-kw))))

             (spec-maybe-type? htype)
             (let [spec-id (halite-types/spec-id (unwrap-maybe htype))
                   recur? (or (contains? spec-bound var-kw)
                              (every? #(not= % spec-id) parent-spec-ids))
                   optional? (halite-types/maybe-type? htype)
                   sub-bound (get spec-bound var-kw :Unset)
                   flattened-vars (when recur?
                                    (flatten-vars sctx
                                                  (conj parent-spec-ids (unwrap-maybe (:$type spec-bound)))
                                                  (str prefix (name var-kw) "|")
                                                  (or already-optional? optional?)
                                                  (if (not= :Unset sub-bound) sub-bound {:$type spec-id})))]
               (cond-> vars
                 recur? (assoc var-kw flattened-vars)

                 (not optional?) (update ::mandatory into (::mandatory flattened-vars))

                 optional? (assoc-in [var-kw :$witness] [(keyword (str prefix (name var-kw) "?")) "Boolean"])))

             :else (throw (ex-info (format "BUG! Variables of type '%s' not supported yet" htype)
                                   {:var-kw var-kw :type htype})))))
       {::mandatory #{} ::spec-id spec-id}
       (->> spec-id sctx :spec-vars))
      (assoc ::refines-to (->> (tree-seq (constantly true) #(map spec-refinements (keys %)) (spec-refinements spec-id))
                               (apply concat)
                               (into {}
                                     (map (fn [[dest-spec-id _]]
                                            [dest-spec-id (-> (flatten-vars sctx
                                                                            (conj parent-spec-ids dest-spec-id)
                                                                            (refined-var-name prefix dest-spec-id)
                                                                            (or already-optional? #_optional?)
                                                                            (assoc (get-in spec-bound [:$refines-to dest-spec-id])
                                                                                   :$type dest-spec-id))
                                                              (dissoc ::spec-id ::refines-to))])))))))))

(defn- leaves [m]
  (if (map? m)
    (mapcat leaves (vals m))
    [m]))

(declare lower-spec-bound)

(s/defn ^:private lower-refines-to-bound
  [optional-context? :- s/Bool, refinements :- FlattenedRefinementMap choco-bound refine-to-map]
  (->> refine-to-map
       (reduce (fn [choco-bound [dest-spec-id bound-map]]
                 (merge choco-bound
                        ;; awkwardly use lower-spec-bound for a FlattenedRefinement
                        (lower-spec-bound (assoc (get refinements dest-spec-id)
                                                 ::spec-id dest-spec-id
                                                 ::mandatory #{}
                                                 ::refines-to {})
                                          optional-context?
                                          (assoc bound-map :$type dest-spec-id))))
               choco-bound)))

(s/defn ^:private lower-spec-bound :- prop-strings/SpecBound
  ([vars :- FlattenedVars, spec-bound :- ConcreteSpecBound]
   (lower-spec-bound vars false spec-bound))
  ([vars :- FlattenedVars, optional-context? :- s/Bool, spec-bound :- ConcreteSpecBound]
   (reduce
    (fn [choco-bounds [var-kw bound]]
      (if (= :$refines-to var-kw)
        (lower-refines-to-bound optional-context? (::refines-to vars) choco-bounds bound)
        (let [composite-var? (map? (vars var-kw))
              choco-var (when-not composite-var?
                          (or (some-> var-kw vars first)
                              (throw (ex-info "BUG! No choco var for var in spec bound"
                                              {:vars vars :spec-bound spec-bound :var-kw var-kw}))))
              witness-var (when composite-var?
                            (some-> var-kw vars :$witness first))]
          (cond
            (or (int? bound) (boolean? bound) (string? bound))
            (assoc choco-bounds choco-var (if optional-context? {:$in #{bound :Unset}} bound))

            (= :Unset bound)
            (if composite-var?
              (if witness-var
                (assoc choco-bounds witness-var false)
                (throw (ex-info (format "Invalid bounds: %s is not an optional variable, and cannot be unset" (name var-kw))
                                {:vars vars :var-kw var-kw :bound bound})))
              (assoc choco-bounds choco-var :Unset))

            (and (map? bound) (contains? bound :$in))
            (let [range-or-set (:$in bound)]
              (when composite-var?
                (throw (ex-info (format "Invalid bound for composite var %s" (name var-kw))
                                {:var-kw var-kw :bound bound})))
              (if (or (vector? range-or-set) (set? range-or-set))
                (assoc choco-bounds choco-var
                       {:$in
                        (cond
                          (and (set? range-or-set) optional-context?) (conj range-or-set :Unset)
                          (and (vector range-or-set) (= 2 (count range-or-set)) optional-context?) (conj range-or-set :Unset)
                          :else range-or-set)})
                (throw (ex-info (format "Invalid bound for %s" (name var-kw))
                                {:var-kw var-kw :bound bound}))))

            (and (map? bound) (contains? bound :$type))
            (let [optional? (and (vector? (:$type bound)) (= :Maybe (first (:$type bound))))
                  htype (halite-types/concrete-spec-type (cond-> (:$type bound) optional? (second)))]
              (when-not composite-var?
                (throw (ex-info (format "Invalid bound for %s, which is not composite" (name var-kw))
                                {:var-kw var-kw :bound bound})))
              (when (and (nil? witness-var) (halite-types/maybe-type? htype))
                (throw (ex-info (format "Invalid bound for %s, which is not optional" (name var-kw))
                                {:var-kw var-kw :bound bound})))
              (-> choco-bounds
                  (merge (lower-spec-bound (vars var-kw) (or optional-context? optional?) bound))
                  (cond-> (and witness-var (not optional?) (not optional-context?))
                    (assoc witness-var true))))

            :else (throw (ex-info (format "Invalid bound for %s" (name var-kw))
                                  {:var-kw var-kw :bound bound}))))))
    {}
    (dissoc spec-bound :$type))))

(s/defn ^:private optionality-constraint :- s/Any
  [senv :- (s/protocol envs/SpecEnv), flattened-vars :- FlattenedVars]
  (let [witness-var (->> flattened-vars :$witness first symbol)
        mandatory-vars (::mandatory flattened-vars)
        mandatory-clause (when (seq mandatory-vars)
                           (apply list '= witness-var (map #(list 'if-value (symbol %) true false) (sort mandatory-vars))))
        optional-clauses (->> (dissoc flattened-vars ::spec-id)
                              (remove (comp mandatory-vars first second))
                              (filter (fn [[var-kw info]]
                                        (if (vector? info)
                                          (halite-types/maybe-type? (envs/halite-type-from-var-type senv (second info)))
                                          (contains? info :$witness))))
                              (sort-by first)
                              (map (fn [[opt-var-kw info]]
                                     (list '=>
                                           (if (vector? info)
                                             (list 'if-value (symbol (first info)) true false)
                                             (symbol (first (:$witness info))))
                                           witness-var))))]
    (mk-junct 'and (cond->> optional-clauses
                     mandatory-clause (cons mandatory-clause)))))

(s/defn ^:private optionality-constraints :- envs/SpecInfo
  [senv :- (s/protocol envs/SpecEnv), flattened-vars :- FlattenedVars, spec-info :- envs/SpecInfo]
  (->> flattened-vars
       (filter (fn [[var-kw info]] (and (map? info) (contains? info :$witness))))
       (reduce
        (fn [spec-info [var-kw info]]
          (let [cexpr (optionality-constraint senv info)
                spec-info (if (= true cexpr)
                            spec-info
                            (->> [(str "$" (name (first (:$witness info)))) cexpr]
                                 (update spec-info :constraints conj)))]
            (optionality-constraints senv info spec-info)))
        spec-info)))

(s/defn ^:private guard-optional-instance-literal
  [[witness-kw htype] mandatory inst-expr]
  (->> mandatory
       (reduce
        (fn [inst-expr var-kw]
          (list 'if-value (symbol var-kw) inst-expr '$no-value))
        inst-expr)
       (list 'when (symbol witness-kw))))

(s/defn ^:private flattened-vars-as-instance-literal
  [{::keys [mandatory spec-id] :keys [$witness] :as flattened-vars} :- FlattenedVars]
  (cond->>
   (reduce
    (fn [inst-expr [var-kw v]]
      (assoc inst-expr var-kw
             (if (vector? v)
               (symbol (first v))
               (flattened-vars-as-instance-literal v))))
    {:$type spec-id}
    (dissoc flattened-vars ::spec-id ::mandatory ::refines-to :$witness))
    $witness (guard-optional-instance-literal $witness mandatory)))

(s/defn ^:private refinement-equality-constraints :- [envs/NamedConstraint]
  [constraint-name-prefix :- s/Str,
   flattened-vars :- FlattenedVars]
  (concat
   ;; constraints for this composition level
   (let [from-instance (flattened-vars-as-instance-literal flattened-vars)]
     (->>
      (::refines-to flattened-vars)
      (mapcat (fn [[to-spec-id to-vars]] ;; to-var is a FlattenedRefinement
                (let [witness-kw (first (:$witness flattened-vars))
                      to-instance (flattened-vars-as-instance-literal
                                   (conj to-vars
                                         [::spec-id to-spec-id]
                                         [::refines-to {}]
                                         (when-let [w (:$witness flattened-vars)]
                                           [:$witness w])))]
                  (concat
                   ;; instance equality
                   [[(str "$refine" constraint-name-prefix "-to-" to-spec-id)
                     (if-not witness-kw
                       (list '=
                             (list 'refine-to from-instance to-spec-id)
                             to-instance)
                       (list 'let ['$from from-instance]
                             (list 'if-value '$from
                                   (list '=
                                         (list 'refine-to '$from to-spec-id)
                                         to-instance)
                                   true)))]]
                   ;; recurse into spec-types
                   (->> (dissoc to-vars ::mandatory)
                        (mapcat (fn [[var-kw v]]
                                  (when (map? v)
                                    (refinement-equality-constraints
                                     (str constraint-name-prefix "-" to-spec-id "|" var-kw)
                                     v)))))
                   ;; equate inner mandatory provided to outer provided, for tighter bounds
                   (when witness-kw
                     (->> (::mandatory to-vars)
                          (mapcat (fn [flat-kw]
                                    [[(str "$refines" witness-kw "=" flat-kw "?")
                                      (list '=
                                            (symbol witness-kw)
                                            (list 'if-value (symbol flat-kw) true false))]]))))))))))
   ;; recurse into spec-typed vars
   (->> (dissoc flattened-vars ::spec-id ::mandatory ::refines-to)
        (mapcat (fn [[var-kw v]]
                  (when (map? v)
                    (refinement-equality-constraints (str constraint-name-prefix "|" (name var-kw))
                                                     v)))))))

(s/defn ^:private add-refinement-equality-constraints :- envs/SpecInfo
  [flattened-vars :- FlattenedVars
   spec-info :- envs/SpecInfo]
  (update spec-info :constraints into (refinement-equality-constraints "" flattened-vars)))

(defn- spec-ify-bound*
  [flattened-vars sctx spec-bound]
  (->>
   {:spec-vars (->> flattened-vars leaves (filter vector?) (into {}))
    :constraints {"vars" (list 'valid? (flattened-vars-as-instance-literal flattened-vars))}}
   (optionality-constraints (ssa/as-spec-env sctx) flattened-vars)
   (add-refinement-equality-constraints flattened-vars)))

(s/defn spec-ify-bound :- envs/SpecInfo
  "Compile the spec-bound into a self-contained halite spec that explicitly states the constraints
  implied by the bound. The resulting spec is self-contained in the sense that:
    * it references no other specs, and
    * it directly incorporates as many spec constraints as possible

  The expressed bound is 'sound', in the sense that every valid instance of the bounded spec
  can be translated into a valid instance of the bound.
  However, the expressed bound is generally not 'tight': there will usually be valid instances of the bound
  that do not correspond to any valid instance of the bounded spec."
  [sctx :- ssa/SpecCtx, spec-bound :- ConcreteSpecBound]
  ;; First, flatten out the variables we'll need.
  (spec-ify-bound* (flatten-vars sctx spec-bound) sctx spec-bound))

;;;;;;;;;; Convert choco bounds to spec bounds ;;;;;;;;;

(s/defn ^:private simplify-atom-bound :- AtomBound
  [bound :- AtomBound]
  (if-let [in (:$in bound)]
    (cond
      (and (set? in) (= 1 (count in))) (first in)
      (and (vector? in) (= (first in) (second in))) (first in)
      :else bound)
    bound))

(s/defn ^:private to-atom-bound :- AtomBound
  [choco-bounds :- prop-strings/SpecBound
   var-type :- halite-types/HaliteType
   [var-kw _] :- FlattenedVar]
  (let [bound (-> var-kw choco-bounds)]
    (simplify-atom-bound
     (if-not (map? bound)
       bound ;; simple value
       (let [bound (:$in bound)]
         {:$in
          (if (halite-types/maybe-type? var-type)
            bound ;; leave any :Unset in the bounds
            (cond ;; remove any :Unset in the bounds
              (vector? bound) (subvec bound 0 2)
              (set? bound) (disj bound :Unset)))})))))

(s/defn ^:private to-spec-bound :- (s/conditional
                                    map? ConcreteSpecBound
                                    :else (s/eq :Unset))
  [choco-bounds :- prop-strings/SpecBound
   senv :- (s/protocol envs/SpecEnv)
   flattened-vars :- FlattenedVars]
  (let [spec-id (::spec-id flattened-vars)
        spec-vars (:spec-vars (envs/system-lookup-spec senv spec-id))
        spec-type (case (some-> flattened-vars :$witness first choco-bounds)
                    false :Unset
                    (nil true) spec-id
                    [:Maybe spec-id])
        refine-to-pairs (seq (::refines-to flattened-vars))]
    (if (= :Unset spec-type)
      :Unset
      (-> {:$type spec-type}
          (into (->> (dissoc flattened-vars ::mandatory :$witness ::spec-id ::refines-to)
                     (map (fn [[k v]]
                            (let [htype (envs/halite-type-from-var-type
                                         senv (get spec-vars k))]
                              [k (if (spec-maybe-type? htype)
                                   (to-spec-bound choco-bounds senv v)
                                   (to-atom-bound choco-bounds htype v))])))))
          (cond-> refine-to-pairs
            (assoc :$refines-to
                   (->> refine-to-pairs
                        (map (fn [[spec-id flattened-vars]]
                               [spec-id
                                (-> (to-spec-bound choco-bounds
                                                   senv
                                                   (assoc flattened-vars
                                                          ::spec-id spec-id))
                                    (dissoc :$type))]))
                        (into {}))))))))

;;;;;;;;;;;; Main API ;;;;;;;;;;;;;;;;

(def Opts prop-strings/Opts)

(def default-options prop-strings/default-options)

(defn- drop-constraints-except-for-Bounds
  [sctx]
  (reduce
   (fn [sctx [spec-id spec-info]]
     (assoc sctx spec-id
            (cond-> spec-info
              (not= :$propagate/Bounds spec-id) (assoc :constraints {}))))
   {}
   sctx))

(defn- disallow-optional-refinements
  "Our refinement lowering code is not currently correct when the refinements are optional.
  We'll fix it, but until we do, we should at least not emit incorrect results silently."
  [sctx]
  (doseq [[spec-id {:keys [refines-to ssa-graph]}] sctx
          [to-id {:keys [expr]}] refines-to]
    (let [htype (->> expr (ssa/deref-id ssa-graph) ssa/node-type)]
      (when (halite-types/maybe-type? htype)
        (throw (ex-info (format "BUG! Refinement of %s to %s is optional, and propagate does not yet support optional refinements"
                                spec-id to-id)
                        {:sctx sctx})))))
  sctx)

(s/defn propagate :- ConcreteSpecBound
  ([sctx :- ssa/SpecCtx, initial-bound :- ConcreteSpecBound]
   (propagate sctx default-options initial-bound))
  ([sctx :- ssa/SpecCtx, opts :- Opts, initial-bound :- ConcreteSpecBound]
   (let [refinement-graph (loom-graph/digraph (update-vals sctx (comp keys :refines-to)))
         flattened-vars (flatten-vars sctx initial-bound)
         lowered-bounds (lower-spec-bound flattened-vars initial-bound)
         spec-ified-bound (spec-ify-bound* flattened-vars sctx initial-bound)]
     (-> sctx
         (assoc :$propagate/Bounds (ssa/spec-to-ssa (ssa/as-spec-env sctx) spec-ified-bound))
         (disallow-optional-refinements)
         (lowering/lower-refinement-constraints)
         ;; When is lowered to if once, early, so that rules generally only have one control flow form to worry about.
         ;; Consequently, no rewrite rules should introduce new when forms!
         (lowering/lower-when)
         (lowering/eliminate-runtime-constraint-violations)
         (lowering/lower-valid?)
         (drop-constraints-except-for-Bounds)

         ;; We may wish to re-enable a weaker version of this rule that
         ;; eliminates error forms in if, but not if-value (which we can't eliminate).
         ;; Experimentation shows that whenever we can eliminate a conditional, we are likely
         ;; to obtain better propagation bounds.
         ;;(lowering/eliminate-error-forms)

         (rewriting/rewrite-reachable-sctx
          [(rewriting/rule simplify/simplify-do)
           (rewriting/rule lowering/bubble-up-do-expr)
           (rewriting/rule lowering/flatten-do-expr)
           (rewriting/rule simplify-redundant-value!)
           (rewriting/rule simplify-statically-known-value?)
           (rewriting/rule lowering/cancel-get-of-instance-literal-expr)
           (rewriting/rule lowering/lower-comparison-exprs-with-incompatible-types)
           (rewriting/rule lowering/lower-instance-comparison-expr)
           (rewriting/rule lowering/push-if-value-into-if-in-expr)
           (rewriting/rule lowering/lower-no-value-comparison-expr)
           (rewriting/rule lowering/lower-maybe-comparison-expr)
           (rewriting/rule lowering/push-gets-into-ifs-expr)
           (rewriting/rule lowering/push-refine-to-into-if)
           (rewriting/rule lowering/push-comparison-into-nonprimitive-if-in-expr)
           (rewriting/rule lowering/eliminate-unused-instance-valued-exprs-in-do-expr)
           (rewriting/rule lowering/eliminate-unused-no-value-exprs-in-do-expr)
           ;; This rule needs access to the refinement graph, so we don't use the rule macro.
           {:rule-name "lower-refine-to"
            :rewrite-fn (partial lowering/lower-refine-to-expr refinement-graph)
            :nodes :all}])
         simplify/simplify
         :$propagate/Bounds
         (prop-strings/propagate opts lowered-bounds)
         (to-spec-bound (ssa/as-spec-env sctx) flattened-vars)))))

(defn- int-bound? [bound]
  (or (int? bound)
      (and (map? bound)
           (contains? bound :$in)
           (every? int? (:$in bound)))))

(defn- union-int-bounds* [a b]
  (cond
    (and (int? a) (int? b)) (if (= a b) a #{a b})
    (int? a) (recur b a)
    (and (set? a) (set? b)) (set/union a b)
    (and (set? a) (int? b)) (conj a b)
    (set? a) (recur b a)
    (and (vector? a) (int? b)) [(min (first a) b) (max (second a) b)]
    (and (vector? a) (set? b)) [(apply min (first a) b) (apply max (second a) b)]
    (vector? a) [(min (first a) (first b)) (max (second a) (second b))]
    :else (throw (ex-info "BUG! Couldn't union int bounds" {:a a :b b}))))

(defn- union-int-bounds [a b]
  (when (not (int-bound? b))
    (throw (ex-info "BUG! Tried to union bounds of different kinds" {:a a :b b})))
  (let [result (union-int-bounds* (cond-> a (:$in a) (:$in)) (cond-> b (:$in b) (:$in)))]
    (if (int? result) result {:$in result})))

(defn- bool-bound? [bound]
  (or (boolean? bound)
      (and (map? bound)
           (contains? bound :$in)
           (every? boolean? (:$in bound)))))

(defn- union-bool-bounds [a b]
  (when (not (bool-bound? b))
    (throw (ex-info "BUG! Tried to union bounds of different kinds" {:a a :b b})))
  (let [a (if (map? a) (:$in a) (set [a]))
        b (if (map? b) (:$in b) (set [b]))
        result (set/union a b)]
    (if (= 1 (count result))
      (first result)
      {:$in result})))

(declare union-bounds)

(s/defn union-refines-to-bounds :- RefinementBound
  "The union of two :$refines-to bounds is the union of bounds for each spec-id
  that appears in BOTH. Including a spec-id that appeared in only `a` would
  cause the resulting bound to be narrower than `b`, because :$refines-to is a
  kind of conjunction."
  [a :- (s/maybe RefinementBound), b :- (s/maybe RefinementBound)]
  (reduce
   (fn [result spec-id]
     (assoc result spec-id
            (dissoc
             (union-bounds
              (assoc (spec-id a) :$type spec-id)
              (assoc (spec-id b) :$type spec-id))
             :$type)))
   {}
   (filter (set (keys a)) (keys b))))

(defn- union-spec-bounds [a b]
  (when (not= (:$type a) (:$type b))
    (throw (ex-info "BUG! Tried to union bounds for different spec types" {:a a :b b})))
  (let [refines-to (union-refines-to-bounds (:$refines-to a) (:$refines-to b))]
    (reduce
     (fn [result var-kw]
       (assoc result var-kw
              (cond
                (and (contains? a var-kw) (contains? b var-kw)) (union-bounds (var-kw a) (var-kw b))
                (contains? a var-kw) (var-kw a)
                :else (var-kw b))))
     (cond-> {:$type (:$type a)}
       (not (empty? refines-to)) (assoc :$refines-to refines-to))
     (disj (set (concat (keys a) (keys b))) :$type :$refines-to))))

(defn- allows-unset? [bound]
  (or
   (= :Unset bound)
   (and (map? bound) (contains? bound :$in)
        (let [in (:$in bound)]
          (if (set? in)
            (contains? in :Unset)
            (= :Unset (last in)))))
   (and (map? bound) (contains? bound :$type)
        (vector? (:$type bound))
        (= :Maybe (first (:$type bound))))))

(defn- remove-unset [bound]
  (cond
    (= :Unset bound)
    (throw (ex-info "BUG! Called remove-unset on :Unset" {:bound bound}))

    (or (int? bound) (boolean? bound)) bound

    (and (map? bound) (contains? bound :$in))
    (let [in (:$in bound)]
      (cond
        (set? in) (update bound :$in disj :Unset)
        (vector? in) (update bound :$in subvec 0 2)
        :else (throw (ex-info "BUG! Unrecognized bound" {:bound bound}))))

    (and (map? bound) (contains? bound :$type))
    (update bound :$type #(cond-> % (vector? %) second))

    :else (throw (ex-info "BUG! Unrecognized bound" {:bound bound}))))

(defn- add-unset [bound]
  (cond
    (or (boolean? bound) (int? bound)) {:$in #{bound :Unset}}

    (and (map? bound) (contains? bound :$in))
    (let [in (:$in bound)]
      (cond
        (set? in) (update bound :$in conj :Unset)
        (vector? in) (assoc bound :$in (conj (subvec in 0 2) :Unset))
        :else (throw (ex-info "BUG! Unrecognized bound" {:bound bound}))))

    (and (map? bound) (contains? bound :$type))
    (update bound :$type #(cond->> % (keyword? %) (conj [:Maybe])))

    :else (throw (ex-info "BUG! Unrecognized bound" {:bound bound}))))

(s/defn union-bounds :- ConcreteBound
  [a :- ConcreteBound, b :- ConcreteBound]
  (if (and (= :Unset a) (= :Unset b))
    :Unset
    (let [unset? (or (allows-unset? a) (allows-unset? b))
          a (if (= :Unset a) b a), b (if (= :Unset b) a b)
          a (remove-unset a),      b (remove-unset b)]
      (cond->
       (cond
         (int-bound? a) (union-int-bounds a b)
         (bool-bound? a) (union-bool-bounds a b)
         :else (union-spec-bounds a b))
        unset? add-unset))))
