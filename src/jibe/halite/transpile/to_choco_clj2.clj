;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.to-choco-clj2
  "Another attempt at transpiling halite to choco-clj."
  (:require [clojure.set :as set]
            [weavejester.dependency :as dep]
            [jibe.halite.envs :as halite-envs]
            [jibe.halite.types :as halite-types]
            [jibe.halite.transpile.ssa :as ssa
             :refer [DerivationName Derivations SpecInfo SpecCtx]]
            [jibe.halite.transpile.util :refer [mk-junct]]
            [schema.core :as s]
            [viasat.choco-clj :as choco-clj]))

(declare Bound)

(s/defschema SpecBound
  {:$type halite-types/NamespacedKeyword
   halite-types/BareKeyword (s/recursive #'Bound)})

(s/defschema AtomBound
  (s/cond-pre
   s/Int
   s/Bool
   {:$in (s/cond-pre
          #{(s/cond-pre s/Int s/Bool)}
          [(s/one s/Int :lower) (s/one s/Int :upper)])}))

(s/defschema Bound
  (s/conditional
   :$type SpecBound
   :else AtomBound))



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
                      'get* (compute-guards* dgraph current result (first args))
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
                                           (map #(list 'get* %1 var-kw) arg-ids))))
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
                    (when (and (seq? form) (= 'get* (first form)))
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
                       (list 'get* then-id var-kw)
                       (list 'get* else-id var-kw))))))
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
  "Replace (get* {... :k <subexpr>} :k) with <subexpr>."
  [{:keys [derivations] :as spec-info} :- SpecInfo]
  (->
   (->> spec-info
        :derivations
        (filter (fn [[id [form htype]]]
                  (if (and (seq? form) (= 'get* (first form)))
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

;;;;;;;;; Bound Spec-ification ;;;;;;;;;;;;;;;;

(s/defschema ^:private FlattenedVar
  [(s/one halite-types/BareKeyword :var-kw)
   (s/one (s/enum :Boolean :Integer) :type)])

(s/defschema ^:private FlattenedVars
  {halite-types/BareKeyword (s/cond-pre FlattenedVar (s/recursive #'FlattenedVars))})

(s/defn ^:private flatten-vars :- FlattenedVars
  ([sctx :- SpecCtx, spec-bound :- SpecBound]
   (flatten-vars sctx [] "" spec-bound))
  ([sctx :- SpecCtx, parent-spec-ids :- [halite-types/NamespacedKeyword], prefix :- s/Str, spec-bound :- SpecBound]
   (reduce
    (fn [vars [var-kw htype]]
      (cond
        (#{:Integer :Boolean} htype)
        (assoc vars var-kw
               [(keyword (str prefix (name var-kw)))
                (if-let [val-bound (some-> spec-bound var-kw :$in)]
                  (if (and (set? val-bound) (= :Integer htype) (< 1 (count val-bound)))
                    (throw (ex-info "TODO: enumerated bounds" {:val-bound val-bound :var-kw var-kw})) #_val-bound
                    htype)
                  htype)])

        (halite-types/spec-type? htype)
        (cond-> vars
          (or (contains? spec-bound var-kw)
              (every? #(not= % htype) parent-spec-ids))
          (assoc var-kw
                 (flatten-vars sctx (conj parent-spec-ids (:$type spec-bound)) (str prefix (name var-kw) "|")
                               (get spec-bound var-kw {:$type htype}))))

        :else (throw (ex-info (format "BUG! Variables of type '%' not supported yet" htype)
                              {:var-kw var-kw :type htype}))))
    {}
    (-> spec-bound :$type sctx :spec-vars))))

(defn- leaves [m]
  (if (map? m)
    (mapcat leaves (vals m))
    [m]))

(s/defn ^:private flatten-get-chain :- s/Symbol
  [rename-scope :- FlattenedVars var-kw-stack :- [s/Keyword] expr]
  (cond
    (seq? expr)
    (let [[_get subexpr var-kw] expr]
      (recur rename-scope (cons var-kw var-kw-stack) subexpr))

    (symbol? expr)
    (let [var-kw-stack (vec (cons (keyword expr) var-kw-stack))
          new-var-kw (get-in rename-scope var-kw-stack)]
      (if (nil? new-var-kw)
        (throw (ex-info "Skip constraint, specs didn't unfold sufficiently"
                        {:skip-constraint? true :form expr :rename-scope rename-scope :var-kw-stack var-kw-stack}))
        (symbol (first new-var-kw))))

    :else
    (throw (ex-info "BUG! Not a get chain!"
                    {:form expr :rename-scope rename-scope :var-kw-stack var-kw-stack}))))

(s/defn ^:private flatten-expression
  [rename-scope :- FlattenedVars expr]
  (cond
    (or (integer? expr) (boolean? expr)) expr
    (symbol? expr) (if-let [[var-kw htype] (rename-scope (keyword expr))]
                     (if (#{:Integer :Boolean} htype)
                       (symbol var-kw)
                       (throw (ex-info "BUG! Found 'naked' instance-valued variable reference"
                                       {:expr expr :rename-scope rename-scope})))
                     expr)
    (seq? expr) (let [[op & args] expr]
                  (condp = op
                    'get* (flatten-get-chain rename-scope [] expr)
                    'let (let [[_let bindings body] expr
                               [rename-scope bindings] (reduce
                                                        (fn [[rename-scope bindings] [var-sym expr]]
                                                          [(dissoc rename-scope (keyword var-sym))
                                                           (conj bindings var-sym (flatten-expression rename-scope expr))])
                                                        [rename-scope []]
                                                        (partition 2 bindings))]
                           (list 'let bindings (flatten-expression rename-scope body)))
                    (->> args (map (partial flatten-expression rename-scope)) (apply list op))))
    :else (throw (ex-info "BUG! Cannot flatten expression" {:form expr :rename-scope rename-scope}))))

(s/defn ^:private flatten-spec-constraints
  [sctx :- SpecCtx, tenv :- (s/protocol halite-envs/TypeEnv), vars :- FlattenedVars, spec-bound :- SpecBound, spec-info :- SpecInfo]
  (let [senv (ssa/as-spec-env sctx)
        spec-to-inline (-> spec-bound :$type sctx)
        old-scope (->> spec-to-inline :spec-vars keys (map symbol) set)]
    (reduce
     (fn [{:keys [derivations] :as spec-info} [cname id]]
       (let [expr (ssa/form-from-ssa old-scope (ssa/prune-dgraph (:derivations spec-to-inline) #{id} true) id)
             fexpr (try (flatten-expression vars expr)
                        (catch clojure.lang.ExceptionInfo ex
                          (if (:skip-constraint? (ex-data ex))
                            nil
                            (throw ex))))]
         (if fexpr
           (let [[dgraph id] (ssa/form-to-ssa {:senv senv :tenv tenv :env {} :dgraph derivations} fexpr)]
             (-> spec-info
                 (assoc :derivations dgraph)
                 (update :constraints conj [cname id])))
           spec-info)))
     spec-info
     (:constraints spec-to-inline))))

(s/defn ^:private flatten-spec-bound :- SpecInfo
  [sctx :- SpecCtx, tenv :- (s/protocol halite-envs/TypeEnv), vars :- FlattenedVars, spec-bound :- SpecBound, spec-info :- SpecInfo]
  (let [senv (ssa/as-spec-env sctx)
        spec-vars (-> spec-bound :$type sctx :spec-vars)
        add-equality-constraint
        ,,(fn [{:keys [derivations] :as spec-info} var-kw v]
            (let [var-sym (->> var-kw vars first symbol)
                  [dgraph constraint] (ssa/constraint-to-ssa senv tenv derivations ["$=" (list '= var-sym v)])]
              (-> spec-info
                  (assoc :derivations dgraph)
                  (update :constraints conj constraint))))]
    (reduce
     (fn [{:keys [derivations] :as spec-info} [var-kw v]]
       (cond
         (= v ::skip) spec-info
         
         (or (integer? v) (boolean? v))
         (add-equality-constraint spec-info var-kw v)

         (map? v)
         (condp #(contains? %2 %1) v
           :$type (flatten-spec-bound sctx tenv (vars var-kw) v spec-info)
           :$in (let [val-bound (:$in v)]
                  (cond
                    (set? val-bound)
                    (cond-> spec-info
                      (= 1 (count val-bound)) (add-equality-constraint var-kw (first val-bound)))

                    (vector? val-bound) 
                    (let [[lower upper] val-bound
                          var-sym (->> var-kw vars first symbol)
                          [dgraph constraint] (ssa/constraint-to-ssa
                                               senv tenv derivations
                                               ["$range" (list 'and
                                                               (list '<= lower var-sym)
                                                               (list '<= var-sym upper))])]
                      (-> spec-info
                          (assoc :derivations dgraph)
                          (update :constraints conj constraint)))
                    :else (throw (ex-info "Not a valid bound" {:bound v}))))
           :else (throw (ex-info "Not a valid bound" {:bound v})))

         :else (throw (ex-info "BUG! Unrecognized spec-bound type" {:spec-bound spec-bound :var-kw var-kw :v v}))))
     (flatten-spec-constraints sctx tenv vars spec-bound spec-info)
     (map (fn [[var-kw v]]
            [var-kw
             (if (map? v)
               (get spec-bound var-kw {:$type (get spec-vars var-kw)})
               (get spec-bound var-kw ::skip))])
          vars))))

(s/defn ^:private flatten-constraints :- SpecInfo
  [sctx :- SpecCtx, tenv :- (s/protocol halite-envs/TypeEnv), vars :- FlattenedVars, spec-bound :- SpecBound, spec-info :- SpecInfo]
  (flatten-spec-bound sctx tenv vars spec-bound spec-info))

(s/defn ^:private spec-ify-bound :- SpecInfo
  [sctx :- SpecCtx, spec-bound :- SpecBound]
  (let [{:keys [spec-vars constraints refines-to derivations] :as spec-info} (-> spec-bound :$type sctx)]
    (when (seq refines-to)
      (throw (ex-info (format "BUG! Cannot spec-ify refinements") {:spec-info spec-info})))
    (let [senv (ssa/as-spec-env sctx)
          flattened-vars (flatten-vars sctx spec-bound)
          new-spec {:spec-vars (->> flattened-vars leaves (into {}))
                    :constraints []
                    :refines-to {}
                    :derivations {}}
          tenv (halite-envs/type-env-from-spec (ssa/as-spec-env sctx) (dissoc new-spec :derivations))]
      (flatten-constraints sctx tenv flattened-vars spec-bound new-spec))))

;;;;;;;;;;; Conversion to Choco ;;;;;;;;;

(s/defn ^:private to-choco-type :- choco-clj/ChocoVarType
  [var-type :- halite-types/HaliteType]
  (cond
    (= :Integer var-type) :Int
    (= :Boolean var-type) :Bool
    :else (throw (ex-info (format "BUG! Can't convert '%s' to choco var type" var-type) {:var-type var-type}))))

(s/defn ^:private to-choco-spec :- choco-clj/ChocoSpec
  "Convert an L0 resource spec to a Choco spec"
  [spec-info :- halite-envs/SpecInfo]
  {:vars (-> spec-info :spec-vars (update-keys symbol) (update-vals to-choco-type))
   :constraints (->> spec-info :constraints (map second) set)})

;;;;;;;;;;;;;;; Main API ;;;;;;;;;;;;;;;;;

(s/defschema Opts
  {:default-int-bounds [(s/one s/Int :lower) (s/one s/Int :upper)]})

(def default-options
  (s/validate
   Opts
   {:default-int-bounds [-1000 1000]}))

(s/defn transpile :- choco-clj/ChocoSpec
  "Transpile the spec-bound into a semantically equivalent choco-clj spec."
  ([senv :- (s/protocol halite-envs/SpecEnv), spec-bound :- SpecBound]
   (transpile senv spec-bound default-options))
  ([senv :- (s/protocol halite-envs/SpecEnv), spec-bound :- SpecBound, opts :- Opts]
   (binding [ssa/*next-id* (atom 0)]
     (let [spec-id (:$type spec-bound)
           sctx (->> spec-id
                     (ssa/build-spec-ctx senv)
                     (fixpoint lower-instance-comparisons)
                     (fixpoint push-gets-into-ifs)
                     (lower-implicit-constraints)
                     (fixpoint cancel-get-of-instance-literal))]
       (->> spec-bound
            (spec-ify-bound sctx)
            (ssa/spec-from-ssa)
            (to-choco-spec))))))
