;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.propagate
  "Constraint propagation for halite."
  (:require [clojure.string :as str]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.transpile.ssa :as ssa :refer [SpecCtx SpecInfo]]
            [jibe.halite.transpile.lowering :as lowering]
            [jibe.halite.transpile.util :refer [fixpoint mk-junct]]
            [jibe.halite.transpile.simplify :as simplify :refer [simplify-redundant-value! simplify-statically-known-value?]]
            [jibe.halite.transpile.rewriting :as rewriting]
            [schema.core :as s]
            [viasat.choco-clj-opt :as choco-clj]))

(declare Bound)

(s/defschema SpecBound
  {:$type (s/cond-pre [(s/one (s/enum :Maybe) :maybe) (s/one halite-types/NamespacedKeyword :type)]
                      halite-types/NamespacedKeyword)
   (s/optional-key :$refines-to) {halite-types/NamespacedKeyword
                                  {halite-types/BareKeyword (s/recursive #'Bound)}}
   halite-types/BareKeyword (s/recursive #'Bound)})

(s/defschema AtomBound
  (s/cond-pre
   s/Int
   s/Bool
   (s/enum :Unset)
   {:$in (s/cond-pre
          #{(s/cond-pre s/Int s/Bool (s/enum :Unset))}
          [(s/one s/Int :lower) (s/one s/Int :upper) (s/optional (s/enum :Unset) :Unset)])}))

(s/defschema Bound
  (s/conditional
   :$type (s/cond-pre SpecBound (s/enum :Unset))
   :else AtomBound))

;;;;;;;;; Bound Spec-ification ;;;;;;;;;;;;;;;;

(s/defschema ^:private PrimitiveType (s/enum "Boolean" "Integer"))
(s/defschema ^:private PrimitiveMaybeType [(s/one (s/enum :Maybe) :maybe) (s/one PrimitiveType :inner)])

(s/defschema ^:private FlattenedVar
  [(s/one halite-types/BareKeyword :var-kw)
   (s/one (s/cond-pre PrimitiveMaybeType PrimitiveType) :type)])

(declare FlattenedRefinementMap)

;; A FlattenedVar represents a mapping from a SpecBound with vars that are
;; unique within that individual spec instance to choco var names that are
;; unique in the whole composed choco spec, to the depth of composition implied
;; by the given SpecBound. FVs form a tree of the same shape as a SpecBound,
;; with each SpecBound's :$type the same as its corresponding FV's ::spec-id,
;; and every var of that Spec appearing as a key in the FV.
(s/defschema ^:private FlattenedVars
  {::mandatory #{halite-types/BareKeyword}
   ::spec-id halite-types/NamespacedKeyword
   ::refines-to (s/recursive #'FlattenedRefinementMap)
   halite-types/BareKeyword (s/cond-pre FlattenedVar (s/recursive #'FlattenedVars))})

;; A FlattenedRefinement is like a FlattenedVar, but does not support a
;; ::refines-to map because the right place to put such a flattening would be in
;; the FlattenedVar that contains this FlattenedRefinement.
(s/defschema ^:private FlattenedRefinement
  {::mandatory #{halite-types/BareKeyword} ;; will we need this?
   halite-types/BareKeyword (s/cond-pre FlattenedVar FlattenedVars)})

(s/defschema ^:private FlattenedRefinementMap
  {halite-types/NamespacedKeyword FlattenedRefinement})

(defn- primitive-maybe-type?
  [htype]
  (or (#{:Integer :Boolean} htype)
      (and (halite-types/maybe-type? htype) (vector? htype)
           (#{:Integer :Boolean} (second htype)))))

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
  ([senv :- (s/protocol halite-envs/SpecEnv), spec-bound :- SpecBound]
   (flatten-vars senv [] "" false spec-bound))
  ([senv :- (s/protocol halite-envs/SpecEnv)
    parent-spec-ids :- [halite-types/NamespacedKeyword]
    prefix :- s/Str
    already-optional? :- s/Bool
    spec-bound :- SpecBound]
   (let [spec-id (->> spec-bound :$type unwrap-maybe)
         spec-refinements #(:refines-to (halite-envs/lookup-spec senv %))]
     (->
      (reduce
       (fn [vars [var-kw vtype]]
         (let [htype (halite-envs/halite-type-from-var-type senv vtype)]
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
                                    (flatten-vars senv
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
       (->> spec-id (halite-envs/lookup-spec senv) :spec-vars))
      (assoc ::refines-to (->> (tree-seq (constantly true) #(map spec-refinements (keys %)) (spec-refinements spec-id))
                               (apply concat)
                               (into {}
                                     (map (fn [[dest-spec-id _]]
                                            [dest-spec-id (-> (flatten-vars senv
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

(s/defn ^:private lower-spec-bound :- choco-clj/VarBounds
  ([vars :- FlattenedVars, spec-bound :- SpecBound]
   (lower-spec-bound vars false spec-bound))
  ([vars :- FlattenedVars, optional-context? :- s/Bool, spec-bound :- SpecBound]
   (reduce
    (fn [choco-bounds [var-kw bound]]
      (if (= :$refines-to var-kw)
        (lower-refines-to-bound optional-context? (::refines-to vars) choco-bounds bound)
        (let [composite-var? (map? (vars var-kw))
              choco-var (when-not composite-var?
                          (or (some-> var-kw vars first symbol)
                              (throw (ex-info "BUG! No choco var for var in spec bound"
                                              {:vars vars :spec-bound spec-bound :var-kw var-kw}))))
              witness-var (when composite-var?
                            (some-> var-kw vars :$witness first symbol))]
          (cond
            (or (int? bound) (boolean? bound))
            (assoc choco-bounds choco-var (if optional-context? #{bound :Unset} bound))

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
                       (cond
                         (and (set? range-or-set) optional-context?) (conj range-or-set :Unset)
                         (and (vector range-or-set) (= 2 (count range-or-set)) optional-context?) (conj range-or-set :Unset)
                         :else range-or-set))
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
  [senv :- (s/protocol halite-envs/SpecEnv), flattened-vars :- FlattenedVars]
  (let [witness-var (->> flattened-vars :$witness first symbol)
        mandatory-vars (::mandatory flattened-vars)
        mandatory-clause (when (seq mandatory-vars)
                           (apply list '= witness-var (map #(list 'if-value (symbol %) true false) (sort mandatory-vars))))
        optional-clauses (->> (dissoc flattened-vars ::spec-id)
                              (remove (comp mandatory-vars first second))
                              (filter (fn [[var-kw info]]
                                        (if (vector? info)
                                          (halite-types/maybe-type? (halite-envs/halite-type-from-var-type senv (second info)))
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

(s/defn ^:private optionality-constraints :- halite-envs/SpecInfo
  [senv :- (s/protocol halite-envs/SpecEnv), flattened-vars :- FlattenedVars, spec-info :- halite-envs/SpecInfo]
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

(s/defn ^:private refinement-equality-constraints :- [halite-envs/NamedConstraint]
  [constraint-name-prefix :- s/Str,
   flattened-vars :- FlattenedVars]
  (concat
   ;; constraints for this composition level
   (let [from-instance (flattened-vars-as-instance-literal flattened-vars)]
     (->>
      (::refines-to flattened-vars)
      (mapcat (fn [[to-spec-id to-vars]]
                (let [witness-kw (first (:$witness flattened-vars))
                      refinement-check (list '=
                                             (list 'refine-to '$from to-spec-id)
                                             (flattened-vars-as-instance-literal
                                              (assoc to-vars
                                                     :$witness (:$witness flattened-vars)
                                                     ::spec-id to-spec-id
                                                     ::refines-to {})))]
                  (cons
                   [(str "$refine" constraint-name-prefix "-to-" to-spec-id)
                    (list 'let ['$from from-instance]
                          (if witness-kw
                            (list 'if-value '$from refinement-check true)
                            refinement-check))]
                   (->> (dissoc to-vars ::mandatory)
                        (mapcat (fn [[var-kw v]]
                                  (when (map? v)
                                    (refinement-equality-constraints
                                     (str constraint-name-prefix "-" to-spec-id "|" var-kw)
                                     v)))))))))))
   ;; recurse into spec-typed vars
   (->> (dissoc flattened-vars ::spec-id ::mandatory ::refines-to)
        (mapcat (fn [[var-kw v]]
                  (when (map? v)
                    (refinement-equality-constraints (str constraint-name-prefix "|" (name var-kw))
                                                     v)))))))

(s/defn ^:private add-refinement-equality-constraints :- halite-envs/SpecInfo
  [flattened-vars :- FlattenedVars
   spec-info :- halite-envs/SpecInfo]
  (update spec-info :constraints into (refinement-equality-constraints "" flattened-vars)))

(s/defn spec-ify-bound :- halite-envs/SpecInfo
  "Compile the spec-bound into a self-contained halite spec that explicitly states the constraints
  implied by the bound. The resulting spec is self-contained in the sense that:
    * it references no other specs, and
    * it directly incorporates as many spec constraints as possible

  The expressed bound is 'sound', in the sense that every valid instance of the bounded spec
  can be translated into a valid instance of the bound.
  However, the expressed bound is generally not 'tight': there will usually be valid instances of the bound
  that do not correspond to any valid instance of the bounded spec."
  [senv :- (s/protocol halite-envs/SpecEnv), spec-bound :- SpecBound]
  ;; First, flatten out the variables we'll need.
  (let [flattened-vars (flatten-vars senv spec-bound)]
    (->>
     {:spec-vars (->> flattened-vars leaves (filter vector?) (into {}))
      :constraints [["vars" (list 'valid? (flattened-vars-as-instance-literal flattened-vars))]]
      :refines-to {}}
     (optionality-constraints senv flattened-vars)
     (add-refinement-equality-constraints flattened-vars))))

;;;;;;;;;;; Conversion to Choco ;;;;;;;;;

(s/defn ^:private to-choco-type :- choco-clj/ChocoVarType
  [var-type :- halite-envs/VarType]
  (cond
    (or (= [:Maybe "Integer"] var-type) (= "Integer" var-type)) :Int
    (or (= [:Maybe "Boolean"] var-type) (= "Boolean" var-type)) :Bool
    :else (throw (ex-info (format "BUG! Can't convert '%s' to choco var type" var-type) {:var-type var-type}))))

(s/defn ^:private to-choco-spec :- choco-clj/ChocoSpec
  "Convert a spec-ified bound to a Choco spec."
  [senv :- (s/protocol halite-envs/SpecEnv), spec-info :- halite-envs/SpecInfo]
  {:vars (-> spec-info :spec-vars (update-keys symbol) (update-vals to-choco-type))
   :optionals (->> spec-info :spec-vars
                   (filter (comp halite-types/maybe-type?
                                 (partial halite-envs/halite-type-from-var-type senv)
                                 val))
                   (map (comp symbol key)) set)
   :constraints (->> spec-info :constraints (map second) set)})

;;;;;;;;;; Convert choco bounds to spec bounds ;;;;;;;;;

(s/defn ^:private to-atom-bound :- AtomBound
  [choco-bounds :- choco-clj/VarBounds
   var-type :- halite-envs/VarType
   [var-kw _] :- FlattenedVar]
  (let [bound (-> var-kw symbol choco-bounds)]
    (if-not (coll? bound)
      bound ;; simple value
      {:$in
       (if (halite-types/maybe-type? var-type)
         bound ;; leave any :Unset in the bounds
         (cond ;; remove any :Unset in the bounds
           (vector? bound) (subvec bound 0 2)
           (set? bound) (disj bound :Unset)))})))

(s/defn ^:private to-spec-bound :- SpecBound
  [choco-bounds :- choco-clj/VarBounds
   senv :- (s/protocol halite-envs/SpecEnv)
   flattened-vars  :- FlattenedVars]
  (let [spec-id (::spec-id flattened-vars)
        spec-vars (:spec-vars (halite-envs/lookup-spec senv spec-id))
        spec-type (case (some-> flattened-vars :$witness first symbol choco-bounds)
                    false :Unset
                    (nil true) spec-id
                    [:Maybe spec-id])
        refine-to-pairs (seq (::refines-to flattened-vars))]
    (if (= :Unset spec-type)
      :Unset
      (-> {:$type spec-type}
          (into (->> (dissoc flattened-vars ::mandatory :$witness ::spec-id ::refines-to)
                     (map (fn [[k v]]
                            (let [htype (halite-envs/halite-type-from-var-type
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

(s/defschema Opts
  {:default-int-bounds [(s/one s/Int :lower) (s/one s/Int :upper)]})

(def default-options
  (s/validate
   Opts
   {:default-int-bounds [-1000 1000]}))

(defn- drop-constraints-except-for-Bounds
  [sctx]
  (reduce
   (fn [sctx [spec-id spec-info]]
     (assoc sctx spec-id
            (cond-> spec-info
              (not= :$propagate/Bounds spec-id) (assoc :constraints []))))
   {}
   sctx))

(s/defn propagate :- SpecBound
  ([senv :- (s/protocol halite-envs/SpecEnv), initial-bound :- SpecBound]
   (propagate senv default-options initial-bound))
  ([senv :- (s/protocol halite-envs/SpecEnv), opts :- Opts, initial-bound :- SpecBound]
   (binding [choco-clj/*default-int-bounds* (:default-int-bounds opts)]
     (let [flattened-vars (flatten-vars senv initial-bound)
           lowered-bounds (lower-spec-bound flattened-vars initial-bound)
           spec-ified-bound (spec-ify-bound senv initial-bound)
           initial-sctx (-> senv
                            (ssa/build-spec-ctx (:$type initial-bound))
                            (assoc :$propagate/Bounds (ssa/spec-to-ssa senv spec-ified-bound)))
           refinement-graph (loom.graph/digraph (update-vals initial-sctx (comp keys :refines-to)))]
       (-> initial-sctx
           (lowering/lower-refinement-constraints)
           ;; When is lowered to if once, early, so that rules generally only have one control flow form to worry about.
           ;; Conseqeuntly, no rewrite rules should introduce new when forms!
           (lowering/lower-when)
           (lowering/eliminate-runtime-constraint-violations)
           (lowering/lower-valid?)
           (drop-constraints-except-for-Bounds)
           (lowering/eliminate-error-forms)
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
           (ssa/spec-from-ssa)
           (->> (to-choco-spec senv))
           (choco-clj/propagate lowered-bounds)
           (to-spec-bound senv flattened-vars))))))
