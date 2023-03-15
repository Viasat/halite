;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.propagate.prop-composition
  (:require [clojure.set :as set]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.propagate.prop-strings :as prop-strings]
            [com.viasat.halite.transpile.lowering :as lowering]
            [com.viasat.halite.transpile.rewriting :as rewriting]
            [com.viasat.halite.transpile.simplify :as simplify]
            [com.viasat.halite.transpile.ssa :as ssa]
            [com.viasat.halite.transpile.transpile-util :as transpile-util]
            [com.viasat.halite.types :as types]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(declare ConcreteBound)

(s/defschema ConcreteSpecBound
  {:$type (s/cond-pre [(s/one (s/enum :Maybe) :maybe) (s/one types/NamespacedKeyword :type)]
                      types/NamespacedKeyword)
   types/BareKeyword (s/recursive #'ConcreteBound)})

(s/defschema AtomBound
  prop-strings/AtomBound)

(s/defschema ConcreteBound
  (s/conditional
   :$type ConcreteSpecBound
   :else AtomBound))

;;;;;;;;; Bound Spec-ification ;;;;;;;;;;;;;;;;

(s/defschema ^:private PrimitiveType (s/enum :Boolean :Integer))
(s/defschema ^:private PrimitiveMaybeType [(s/one (s/enum :Maybe) :maybe) (s/one PrimitiveType :inner)])

(s/defschema ^:private FlattenedVar
  [(s/one types/BareKeyword :var-kw)
   (s/one (s/cond-pre PrimitiveMaybeType PrimitiveType) :type)])

;; A FlattenedVar represents a mapping from a ConcerteSpecBound with vars that are
;; unique within that individual spec instance to choco var names that are
;; unique in the whole composed choco spec, to the depth of composition implied
;; by the given ConcreteSpecBound. FVs form a tree of the same shape as a ConcreteSpecBound,
;; with each ConcreteSpecBound's :$type the same as its corresponding FV's ::spec-id,
;; and every var of that Spec appearing as a key in the FV.
(s/defschema ^:private FlattenedVars
  {::mandatory #{types/BareKeyword}
   ::spec-id types/NamespacedKeyword
   types/BareKeyword (s/cond-pre FlattenedVar (s/recursive #'FlattenedVars))})

(defn- primitive-maybe-type?
  [htype]
  (or (#{:Integer :Boolean :String} htype)
      (and (types/maybe-type? htype) (vector? htype)
           (#{:Integer :Boolean :String} (second htype)))))

(defn unwrap-maybe [htype]
  (cond-> htype
    (and (vector? htype) (= :Maybe (first htype))) second))

(s/defn ^:private flatten-vars :- FlattenedVars
  ([sctx :- ssa/SpecCtx, spec-bound :- ConcreteSpecBound]
   (flatten-vars sctx [] "" false spec-bound))
  ([sctx :- ssa/SpecCtx
    parent-spec-ids :- [types/NamespacedKeyword]
    prefix :- s/Str
    already-optional? :- s/Bool
    spec-bound :- ConcreteSpecBound]
   (let [spec-id (->> spec-bound :$type unwrap-maybe)
         senv (ssa/as-spec-env sctx)]
     (reduce
      (fn [vars [var-kw htype]]
        (cond
          (primitive-maybe-type? htype)
          (let [actually-mandatory? (and already-optional? (not (types/maybe-type? htype)))
                prefixed-var-kw (keyword (str prefix (name var-kw)))]
            (-> vars
                (assoc var-kw [prefixed-var-kw
                               (cond->> htype
                                 actually-mandatory? types/maybe-type)])
                (cond->
                 actually-mandatory? (update ::mandatory conj prefixed-var-kw))))

          (types/spec-maybe-type? htype)
          (let [spec-id (types/spec-id (unwrap-maybe htype))
                recur? (or (contains? spec-bound var-kw)
                           (every? #(not= % spec-id) parent-spec-ids))
                optional? (types/maybe-type? htype)
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

              optional? (assoc-in [var-kw :$witness] [(keyword (str prefix (name var-kw) "?")) :Boolean])))

          :else (throw (ex-info (format "BUG! Variables of type '%s' not supported yet" htype)
                                {:var-kw var-kw :type htype}))))
      {::mandatory #{} ::spec-id spec-id}
      (->> spec-id sctx :fields)))))

(defn- leaves [m]
  (if (map? m)
    (mapcat leaves (vals m))
    [m]))

(declare lower-spec-bound)

(s/defn ^:private lower-spec-bound :- prop-strings/SpecBound
  ([vars :- FlattenedVars, spec-bound :- ConcreteSpecBound]
   (lower-spec-bound vars false spec-bound))
  ([vars :- FlattenedVars, optional-context? :- s/Bool, spec-bound :- ConcreteSpecBound]
   (reduce
    (fn [choco-bounds [var-kw bound]]
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
          (let [optional-bound? (and (vector? (:$type bound)) (= :Maybe (first (:$type bound))))
                optional? (and (boolean witness-var) optional-bound?)
                htype (types/concrete-spec-type (cond-> (:$type bound) optional-bound? (second)))]
            (when-not composite-var?
              (throw (ex-info (format "Invalid bound for %s, which is not composite" (name var-kw))
                              {:var-kw var-kw :bound bound})))
            (-> choco-bounds
                (merge (lower-spec-bound (vars var-kw) (or optional-context? optional?) bound))
                (cond-> (and witness-var (not optional?) (not optional-context?))
                  (assoc witness-var true))))

          :else (throw (ex-info (format "Invalid bound for %s" (name var-kw))
                                {:var-kw var-kw :bound bound})))))
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
                                          (types/maybe-type? (second info))
                                          (contains? info :$witness))))
                              (sort-by first)
                              (map (fn [[opt-var-kw info]]
                                     (list '=>
                                           (if (vector? info)
                                             (list 'if-value (symbol (first info)) true false)
                                             (symbol (first (:$witness info))))
                                           witness-var))))]
    (transpile-util/mk-junct 'and (cond->> optional-clauses
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
    (dissoc flattened-vars ::spec-id ::mandatory :$witness))
    $witness (guard-optional-instance-literal $witness mandatory)))

(defn- spec-ify-bound*
  [flattened-vars sctx spec-bound]
  (->>
   {:fields (->> flattened-vars leaves (filter vector?) (into {}))
    :constraints [["vars" (list 'valid? (flattened-vars-as-instance-literal flattened-vars))]]}
   (optionality-constraints (ssa/as-spec-env sctx) flattened-vars)))

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
   var-type :- types/HaliteType
   [var-kw _] :- FlattenedVar]
  (let [bound (-> var-kw choco-bounds)]
    (simplify-atom-bound
     (if-not (map? bound)
       bound ;; simple value
       (let [bound (:$in bound)]
         {:$in
          (if (types/maybe-type? var-type)
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
        fields (:fields (envs/lookup-spec senv spec-id))
        spec-type (case (some-> flattened-vars :$witness first choco-bounds)
                    false :Unset
                    (nil true) spec-id
                    [:Maybe spec-id])]
    (if (= :Unset spec-type)
      :Unset
      (-> {:$type spec-type}
          (into (->> (dissoc flattened-vars ::mandatory :$witness ::spec-id)
                     (map (fn [[k v]]
                            (let [htype (get fields k)]
                              [k (if (types/spec-maybe-type? htype)
                                   (to-spec-bound choco-bounds senv v)
                                   (to-atom-bound choco-bounds htype v))])))))))))

;;;;;;;;;;;; Main API ;;;;;;;;;;;;;;;;

(def Opts prop-strings/Opts)

(def default-options prop-strings/default-options)

(defn- drop-constraints-except-for-Bounds
  [sctx]
  (reduce
   (fn [sctx [spec-id spec-info]]
     (ssa/add-spec-to-context sctx spec-id
                              (cond-> spec-info
                                (not= :$propagate/Bounds spec-id) (assoc :constraints []))))
   {}
   sctx))

(s/defn propagate :- ConcreteSpecBound
  ([sctx :- ssa/SpecCtx, initial-bound :- ConcreteSpecBound]
   (propagate sctx default-options initial-bound))
  ([sctx :- ssa/SpecCtx, opts :- Opts, initial-bound :- ConcreteSpecBound]
   (let [flattened-vars (flatten-vars sctx initial-bound)
         lowered-bounds (lower-spec-bound flattened-vars initial-bound)
         spec-ified-bound (spec-ify-bound* flattened-vars sctx initial-bound)]
     (-> sctx
         (ssa/add-spec-to-context :$propagate/Bounds (ssa/spec-to-ssa (ssa/as-spec-env sctx) spec-ified-bound))
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
           (rewriting/rule simplify/simplify-redundant-value!)
           (rewriting/rule simplify/simplify-statically-known-value?)
           (rewriting/rule lowering/cancel-get-of-instance-literal-expr)
           (rewriting/rule lowering/lower-comparison-exprs-with-incompatible-types)
           (rewriting/rule lowering/lower-instance-comparison-expr)
           (rewriting/rule lowering/push-if-value-into-if-in-expr)
           (rewriting/rule lowering/lower-no-value-comparison-expr)
           (rewriting/rule lowering/lower-maybe-comparison-expr)
           (rewriting/rule lowering/push-gets-into-ifs-expr)
           (rewriting/rule lowering/push-comparison-into-nonprimitive-if-in-expr)
           (rewriting/rule lowering/eliminate-unused-instance-valued-exprs-in-do-expr)
           (rewriting/rule lowering/eliminate-unused-no-value-exprs-in-do-expr)])
         simplify/simplify
         :$propagate/Bounds
         (prop-strings/propagate opts lowered-bounds)
         (to-spec-bound (ssa/as-spec-env sctx) flattened-vars)))))
