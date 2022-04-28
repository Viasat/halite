;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.to-choco-clj2
  "Another attempt at transpiling halite to choco-clj."
  (:require [clojure.set :as set]
            [weavejester.dependency :as dep]
            [jibe.halite.envs :as halite-envs]
            [jibe.halite.types :as halite-types]
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


;;;;;;;;;;;;;;;; SSA Pass ;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private choco-ops
  '#{dec inc + - * < <= > >= and or not => div mod expt abs = if not= let})

(def ^:private renamed-ops
  '{mod* mod})

(def ^:private supported-halite-ops
  (into choco-ops (concat (keys renamed-ops) '#{get* #_refine-to})))

(s/defschema DerivationName
  (s/constrained s/Symbol #(re-matches #"\$[1-9][0-9]*" (name %))))

(s/defschema SSATerm
  (s/cond-pre
   s/Int
   s/Bool
   s/Symbol))

(s/defschema SSAOp (apply s/enum (disj supported-halite-ops 'let)))

(s/defschema SSAForm
  (s/cond-pre
   SSATerm
   ;;#{SSATerm}
   {:$type halite-types/NamespacedKeyword
    s/Keyword SSATerm}
   [(s/one SSAOp :op) (s/cond-pre DerivationName s/Keyword)]))

(s/defschema Derivation
  [(s/one SSAForm :form) (s/one halite-types/HaliteType :type) (s/optional DerivationName :negation)])

(s/defn ^:private referenced-derivations :- [DerivationName]
  "Return the derivation nodes directly referenced by a given derivation.
  When the derivation is boolean, its negation node is NOT included."
  [[form htype :as deriv] :- Derivation]
  (cond
    (seq? form) (->> form rest (filter symbol?))
    (map? form) (-> form (dissoc :$type) vals)
    ;; this is a bit of a hack
    (nil? (s/check DerivationName form)) [form]
    :else []))

(s/defschema Derivations
  {DerivationName Derivation})

(s/defn ^:private deref-id :- Derivation
  "Return the derivation in dgraph referenced by id, possibly
  indirectly."
  [dgraph :- Derivations id]
  (loop [[form :as d] (dgraph id)]
    (if (nil? (s/check DerivationName form))
      (recur (dgraph form))
      d)))

(s/defn ^:private reachable-derivations :- #{DerivationName}
  "Return the set of derivation nodes transitively reachable from the given id. For reachable boolean
  nodes, their negation nodes are included when include-negations? is true."
  ([dgraph :- Derivations, include-negations? :- s/Bool, id :- DerivationName]
   (reachable-derivations dgraph include-negations? #{} id))
  ([dgraph :- Derivations, include-negations? :- s/Bool, reached :- #{DerivationName}, id :- DerivationName]
   (loop [[next-id & ids] [id]
          reached reached]
     (cond
       (nil? next-id) reached
       (reached next-id) (recur ids reached)
       :else (let [[form htype neg-id :as d] (dgraph next-id)]
               (recur
                (into ids (referenced-derivations d))
                (cond-> (conj reached next-id)
                  (and include-negations? neg-id) (conj neg-id))))))))

(s/defschema SpecInfo
  (assoc halite-envs/SpecInfo
         :derivations Derivations
         :constraints [[(s/one s/Str :cname) (s/one DerivationName :deriv)]]))

(s/defschema DerivResult [(s/one Derivations :derivs) (s/one DerivationName :id)])

(s/defn ^:private find-form :- (s/maybe DerivationName)
  [dgraph :- Derivations, ssa-form :- SSAForm]
  (loop [[[id [form _] :as entry] & entries] dgraph]
    (when entry
      (if (= form ssa-form)
        id
        (recur entries)))))

(s/defn ^:private negated :- DerivationName
  [dgraph :- Derivations, id :- DerivationName]
  (when-not (contains? dgraph id)
    (throw (ex-info "BUG! Asked for negation of expression not in derivation graph"
                    {:dgraph dgraph :id id})))
  (let [[form htype neg-id] (dgraph id)]
    (when (not= htype :Boolean)
      (throw (ex-info "BUG! Asked for negation of a non-boolean expression"
                      {:dgraph dgraph :id id})))
    neg-id))

(def ^:dynamic *next-id*
  "During transpilation, holds an atom whose value is the next globally unique derivation id."
  nil)

(defn- fresh-id! [] (symbol (str "$" (swap! *next-id* inc))))

(declare add-derivation)

(s/defn ^:private add-boolean-derivation :- DerivResult
  "Ensure that ssa-form and its negation is present in dgraph, and that they refer to one another.
  In the process, we can do some normalization of expressions, so that what would otherwise be distinct but
  equivalent nodes in the graph become the same node.

  In particular, we always rewrite

     (not (= ...))  (not= ..)
     (not (< a b))  (<= b a)
     (not (<= a b)) (< b a)
     (> a b)        (< b a)
     (>= a b)       (<= b a)"
  [dgraph :- Derivations, [ssa-form htype :as d] :- Derivation]
  (let [op (when (seq? ssa-form) (first ssa-form))]
    (condp = op
      '= (let [[id neg-id ] (repeatedly 2 fresh-id!)]
           [(assoc dgraph
                   id (conj d neg-id)
                   neg-id [(apply list 'not= (rest ssa-form)) :Boolean id])
            id])

      'not= (let [[dgraph id] (add-derivation dgraph [(apply list '= (rest ssa-form)) :Boolean])]
              [dgraph (negated dgraph id)])

      'not [dgraph (negated dgraph (second ssa-form))]

      '< (let [[id neg-id] (repeatedly 2 fresh-id!)]
           [(assoc dgraph
                   id (conj d neg-id)
                   neg-id [(apply list '<= (reverse (rest ssa-form))) :Boolean id])
            id])

      '> (add-derivation dgraph [(apply list '< (reverse (rest ssa-form))) :Boolean])

      '<= (let [[dgraph id] (add-derivation dgraph [(apply list '> (rest ssa-form)) :Boolean])]
            [dgraph (negated dgraph id)])

      '>= (add-derivation dgraph [(apply list '<= (reverse (rest ssa-form))) :Boolean])

      (condp = ssa-form
        true (let [[id neg-id] (repeatedly 2 fresh-id!)]
               [(assoc dgraph
                       id (conj d neg-id)
                       neg-id [false :Boolean id])
                id])
        false (let [[dgraph id ] (add-derivation dgraph [true :Boolean])]
                [dgraph (negated dgraph id)])
        
        (let [[id neg-id] (repeatedly 2 fresh-id!)]
          [(assoc dgraph
                  id (conj d neg-id)
                  neg-id [(list 'not id) :Boolean id])
           id])))))

(s/defn ^:private add-derivation :- DerivResult
  [dgraph :- Derivations, [ssa-form htype :as d] :- Derivation]
  (if-let [id (find-form dgraph ssa-form)]
    (if (not= htype (get-in dgraph [id 1]))
      (throw (ex-info (format "BUG! Tried to add derivation %s, but that form already recorded as %s" d (dgraph id))
                      {:derivation d :dgraph dgraph}))
      [dgraph id])
    (if (= :Boolean htype)
      (add-boolean-derivation dgraph d)
      (let [id (fresh-id!)]
        [(assoc dgraph id d) id]))))

(s/defn ^:private add-derivation-for-app :- DerivResult
  [dgraph :- Derivations [op & args :as form]]
  (let [op (get renamed-ops op op)]
    (add-derivation
     dgraph
     [(apply list op args)
      (cond
        ('#{+ - * div mod expt abs} op) :Integer
        ('#{< <= > >= and or not => = not=} op) :Boolean
        :else (throw (ex-info (format  "BUG! Couldn't determine type of function application for '%s'" op)
                              {:form form})))])))

(declare form-to-ssa)

(s/defschema SSACtx
  {:senv (s/protocol halite-envs/SpecEnv)
   :tenv (s/protocol halite-envs/TypeEnv)
   :env {s/Symbol DerivationName}
   :dgraph Derivations})

(s/defn ^:private let-to-ssa :- DerivResult
  [ctx :- SSACtx, [_ bindings body :as form]]
  (as-> ctx ctx
    (reduce
     (fn [ctx [var-sym subexpr]]
       (let [[dgraph id] (form-to-ssa ctx subexpr)
             htype (get-in dgraph [id 1])]
         (assoc ctx
                :tenv (halite-envs/extend-scope (:tenv ctx) var-sym htype)
                :env (assoc (:env ctx) var-sym id)
                :dgraph dgraph)))
     ctx
     (partition 2 bindings))
    (form-to-ssa ctx body)))

(s/defn ^:private app-to-ssa :- DerivResult
  [ctx :- SSACtx, [op & args :as form]]
  (let [[dgraph args] (reduce (fn [[dgraph args] arg]
                                (let [[dgraph id] (form-to-ssa (assoc ctx :dgraph dgraph) arg)]
                                  [dgraph (conj args id)]))
                              [(:dgraph ctx) []]
                              args)]
    (add-derivation-for-app dgraph (apply list op args))))

(s/defn ^:private symbol-to-ssa :- DerivResult
  [{:keys [dgraph tenv env]} :- SSACtx, form]
  (cond
    (contains? dgraph form) [dgraph form]
    (contains? env form) [dgraph (env form)]
    :else (let [htype (or (get (halite-envs/scope tenv) form)
                          (throw (ex-info (format "BUG! Undefined: '%s'" form) {:tenv tenv :form form})))]
            (add-derivation dgraph [form htype]))))

(s/defn ^:private if-to-ssa :- DerivResult
  [ctx :- SSACtx, [_ pred then else :as form]]
  (let [[dgraph pred-id] (form-to-ssa ctx pred)
        [dgraph then-id] (form-to-ssa (assoc ctx :dgraph dgraph) then)
        [dgraph else-id] (form-to-ssa (assoc ctx :dgraph dgraph) else)
        htype (halite-types/meet (get-in dgraph [then-id 1]) (get-in dgraph [else-id 1]))]
    (add-derivation dgraph [(list 'if pred-id then-id else-id) htype])))

(s/defn ^:private get-to-ssa :- DerivResult
  [{:keys [dgraph senv] :as ctx} :- SSACtx, [_ subexpr var-kw :as form]]
  (let [[dgraph id] (form-to-ssa ctx subexpr)
        spec-id (->> id dgraph second)
        htype (or (->> spec-id (halite-envs/lookup-spec senv) :spec-vars var-kw)
                  (throw (ex-info (format "BUG! Couldn't determine type of field '%s' of spec '%s'" var-kw spec-id)
                                  {:form form})))]
    (add-derivation dgraph [(list 'get* id var-kw) htype])))

(s/defn ^:private inst-literal-to-ssa :- DerivResult
  [ctx :- SSACtx, form]
  (let [spec-id (:$type form)
        [dgraph inst] (reduce
                       (fn [[dgraph inst] var-kw]
                         (let [[dgraph id] (form-to-ssa (assoc ctx :dgraph dgraph) (get form var-kw))]
                           [dgraph (assoc inst var-kw id)]))
                       [(:dgraph ctx) {:$type spec-id}]
                       (-> form (dissoc :$type) keys sort))]
    (add-derivation dgraph [inst spec-id])))

(s/defn ^:private form-to-ssa :- DerivResult
  ([{:keys [dgraph] :as ctx} :- SSACtx, form]
   (cond
     (int? form) (add-derivation dgraph [form :Integer])
     (boolean? form) (add-derivation dgraph [form :Boolean])
     (symbol? form) (symbol-to-ssa ctx form)
     (seq? form) (let [[op & args] form]
                   (when-not (contains? supported-halite-ops op)
                     (throw (ex-info (format "BUG! Cannot transpile operation '%s'" op) {:form form})))
                   (condp = op
                     'let (let-to-ssa ctx form)
                     'if (if-to-ssa ctx form)
                     'get* (get-to-ssa ctx form)
                     (app-to-ssa ctx form)))
     (map? form) (inst-literal-to-ssa ctx form)
     :else (throw (ex-info "BUG! Unsupported feature in halite->choco-clj transpilation"
                           {:form form}))))
  ([{:keys [dgraph] :as ctx} :- SSACtx, replace-id :- DerivationName, form]
   (let [[dgraph id] (form-to-ssa ctx form)
         [new-form htype] (dgraph id)]
     [(-> dgraph
          (assoc-in [replace-id 0] new-form)
          (dissoc id))
      replace-id])))

(s/defn ^:private constraint-to-ssa :- [(s/one Derivations :dgraph), [(s/one s/Str :cname) (s/one DerivationName :form)]]
  [senv :- (s/protocol halite-envs/SpecEnv), tenv :- (s/protocol halite-envs/TypeEnv), derivations :- Derivations, [cname constraint-form]]
  (let [[derivations id ] (form-to-ssa {:senv senv :tenv tenv :env {} :dgraph derivations} constraint-form)]
    [derivations [cname id]]))

(s/defn ^:private spec-to-ssa :- SpecInfo
  [senv :- (s/protocol halite-envs/SpecEnv), tenv :- (s/protocol halite-envs/TypeEnv), spec-info :- halite-envs/SpecInfo]
  (let [[derivations constraints]
        ,,(reduce
           (fn [[derivations constraints] constraint]
             (let [[derivations constraint] (constraint-to-ssa senv tenv derivations constraint)]
               [derivations (conj constraints constraint)]))
           [{} []]
           (:constraints spec-info))]
    (assoc spec-info
           :derivations derivations
           :constraints constraints)))

(s/defschema SpecCtx
  {halite-types/NamespacedKeyword SpecInfo})

(s/defn ^:private as-spec-env :- (s/protocol halite-envs/SpecEnv)
  [sctx :- SpecCtx]
  (reify halite-envs/SpecEnv
    (lookup-spec* [self spec-id] (some-> sctx spec-id (dissoc :derivations)))))

(defn- spec-ref-from-type [htype]
  (cond
    (and (keyword? htype) (namespace htype)) htype
    (vector? htype) (recur (second htype))
    :else nil))

(defn- spec-refs-from-expr
  [expr]
  (cond
    (integer? expr) #{}
    (boolean? expr) #{}
    (symbol? expr) #{}
    (map? expr) (->> (dissoc expr :$type) vals (map spec-refs-from-expr) (apply set/union #{(:$type expr)}))
    (seq? expr) (let [[op & args] expr]
                  (condp = op
                    'let (let [[bindings body] args]
                           (->> bindings (partition 2) (map (comp spec-refs-from-expr second))
                                (apply set/union (spec-refs-from-expr body))))
                    'get* (spec-refs-from-expr (first args))
                    (apply set/union (map spec-refs-from-expr args))))
    :else (throw (ex-info "BUG! Can't extract spec refs from form" {:form expr}))))

(defn- spec-refs [{:keys [spec-vars refines-to constraints] :as spec-info}]
  (->> spec-vars
       vals
       (map spec-ref-from-type)
       (remove nil?)
       (concat (keys refines-to))
       set
       (set/union
        (->> constraints (map (comp spec-refs-from-expr second)) (apply set/union)))
       ;; TODO: refinement exprs
       ))

(defn- reachable-specs [senv root-spec-id]
  (loop [specs {}
         next-spec-ids [root-spec-id]]
    (if-let [[spec-id & next-spec-ids] next-spec-ids]
      (if (contains? specs spec-id)
        (recur specs next-spec-ids)
        (let [spec-info (halite-envs/lookup-spec senv spec-id)]
          (recur
           (assoc specs spec-id spec-info)
           (into next-spec-ids (spec-refs spec-info)))))
      specs)))

(s/defn ^:private build-spec-ctx :- SpecCtx
  [senv :- (s/protocol halite-envs/SpecEnv), root-spec-id :- halite-types/NamespacedKeyword]
  (-> root-spec-id
      (->> (reachable-specs senv))
      (update-vals #(spec-to-ssa senv (halite-envs/type-env-from-spec senv %) %))))

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
                                not-pred-id (negated dgraph pred-id)]
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
  (let [negated-clauses (->> guards (map #(->> % (map (partial negated dgraph)) set)))]
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

(s/defn ^:private mk-junct :- s/Any
  [op :- (s/enum 'and 'or), clauses :- [s/Any]]
  (condp = (count clauses)
    0 ({'and true, 'or false} op)
    1 (first clauses)
    (apply list op clauses)))

;; Assumes abstract expressions and optional vars have been lowered.

(s/defn ^:private lower-instance-comparisons-in-spec :- SpecInfo
  [sctx :- SpecCtx, {:keys [derivations] :as spec-info} :- SpecInfo]
  (let [senv (as-spec-env sctx)
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
                   (form-to-ssa
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

(declare prune-derivations)

(s/defn ^:private push-gets-into-ifs-in-spec :- SpecInfo
  [sctx :- SpecCtx, {:keys [derivations] :as spec-info} :- SpecInfo]
  (let [senv (as-spec-env sctx)
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
                (form-to-ssa
                 (assoc ctx :dgraph dgraph)
                 get-id
                 (list 'if pred-id
                       (list 'get* then-id var-kw)
                       (list 'get* else-id var-kw))))))
           derivations)
          (assoc spec-info :derivations))
     (prune-derivations false))))

(s/defn ^:private push-gets-into-ifs :- SpecCtx
  [sctx :- SpecCtx]
  (update-vals sctx (partial push-gets-into-ifs-in-spec sctx)))

;;;;;;;;; Instance Literal Lowering ;;;;;;;;;;;

(declare spec-from-ssa)

(s/defn ^:private lower-implicit-constraints-in-spec :- SpecInfo
  [sctx :- SpecCtx, spec-info :- SpecInfo]
  (let [guards (compute-guards spec-info)
        inst-literals (->> spec-info
                           :derivations
                           (filter (fn [[id [form htype]]] (map? form))))
        senv (as-spec-env sctx)
        tenv (halite-envs/type-env-from-spec senv (dissoc spec-info :derivations))]
    (reduce
     (fn [{:keys [derivations] :as spec-info} [id [inst-literal htype]]]
       (let [spec-id (:$type inst-literal)
             guard-form (->> id guards (map #(mk-junct 'and (sort %))) (mk-junct 'or))
             constraints (->> spec-id sctx spec-from-ssa :constraints (map second))
             constraint-expr (list 'let (vec (mapcat (fn [[var-kw id]] [(symbol var-kw) id]) (dissoc inst-literal :$type)))
                                   (mk-junct 'and constraints))
             constraint-expr (if (not= true guard-form)
                               (list 'if guard-form constraint-expr true)
                               constraint-expr)
             [derivations id] (form-to-ssa {:senv senv :tenv tenv :env {} :dgraph derivations} constraint-expr)]
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
                    (let [[subform] (deref-id derivations (second form))]
                      (map? subform)))))
        (reduce
         (fn [dgraph [id [[_get subid var-kw] htype]]]
           (let [[inst-form] (deref-id dgraph subid)
                 field-node (deref-id dgraph (get inst-form var-kw))]
             (assoc dgraph id field-node)))
         derivations)
        (assoc spec-info :derivations))
   (prune-derivations false)))

(s/defn ^:private cancel-get-of-instance-literal :- SpecCtx
  [sctx :- SpecCtx]
  (update-vals sctx cancel-get-of-instance-literal-in-spec))

;;;;;;;;;; Pruning unreachable expressions ;;;;;;;;;;;

;; Expression rewriting in the lowering passes may end up making expression nodes
;; unreachable from the 'roots' of the derivations graph. This shouldn't affect semantics,
;; but it may produce unnecessary let bindings when the graph is turned back into expressions.
;; To avoid this, we prune unreachable expressions from the graph after lowering phases that might
;; introduce them.
;; HOWEVER! When a form is put into SSA, (possibly unreachable) nodes representing the negation of
;; each boolean expression are added, and pruning will remove them! So, don't prune until you're "done".

(s/defn ^:private prune-dgraph :- Derivations
  [dgraph :- Derivations, roots :- #{DerivationName}, prune-negations? :- s/Bool]
  (let [reachable (reduce
                   (fn [reachable id]
                     (reachable-derivations dgraph (not prune-negations?) reachable id))
                   #{}
                   roots)]
    (->> dgraph
         (filter #(reachable (first %)))
         (into {}))))

(s/defn ^:private prune-derivations :- SpecInfo
  [{:keys [constraints] :as spec-info} :- SpecInfo, prune-negations? :- s/Bool]
  (let [roots (->> constraints (map second) set)]
    (update spec-info :derivations prune-dgraph roots prune-negations?)))

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

(declare form-from-ssa)

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
  (let [senv (as-spec-env sctx)
        spec-to-inline (-> spec-bound :$type sctx)
        old-scope (->> spec-to-inline :spec-vars keys (map symbol) set)]
    (reduce
     (fn [{:keys [derivations] :as spec-info} [cname id]]
       (let [expr (form-from-ssa old-scope (prune-dgraph (:derivations spec-to-inline) #{id} true) id)
             fexpr (try (flatten-expression vars expr)
                        (catch clojure.lang.ExceptionInfo ex
                          (if (:skip-constraint? (ex-data ex))
                            nil
                            (throw ex))))]
         (if fexpr
           (let [[dgraph id] (form-to-ssa {:senv senv :tenv tenv :env {} :dgraph derivations} fexpr)]
             (-> spec-info
                 (assoc :derivations dgraph)
                 (update :constraints conj [cname id])))
           spec-info)))
     spec-info
     (:constraints spec-to-inline))))

(s/defn ^:private flatten-spec-bound :- SpecInfo
  [sctx :- SpecCtx, tenv :- (s/protocol halite-envs/TypeEnv), vars :- FlattenedVars, spec-bound :- SpecBound, spec-info :- SpecInfo]
  (let [senv (as-spec-env sctx)
        spec-vars (-> spec-bound :$type sctx :spec-vars)
        add-equality-constraint
        ,,(fn [{:keys [derivations] :as spec-info} var-kw v]
            (let [var-sym (->> var-kw vars first symbol)
                  [dgraph constraint] (constraint-to-ssa senv tenv derivations ["$=" (list '= var-sym v)])]
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
                          [dgraph constraint] (constraint-to-ssa
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
    (let [senv (as-spec-env sctx)
          flattened-vars (flatten-vars sctx spec-bound)
          new-spec {:spec-vars (->> flattened-vars leaves (into {}))
                    :constraints []
                    :refines-to {}
                    :derivations {}}
          tenv (halite-envs/type-env-from-spec (as-spec-env sctx) (dissoc new-spec :derivations))]
      (flatten-constraints sctx tenv flattened-vars spec-bound new-spec))))

;;;;;;;;; Converting from SSA back into a more readable form ;;;;;;;;

(s/defn ^:private topo-sort :- [DerivationName]
  [derivations :- Derivations]
  (->> derivations
       (reduce
        (fn [g [id d]]
          (reduce #(dep/depend %1 id %2) g (referenced-derivations d)))
        (dep/graph))
       (dep/topo-sort)))

(s/defn ^:private form-from-ssa*
  [dgraph :- Derivations, bound? :- #{s/Symbol} id]
  (if (bound? id)
    id
    (let [[form _] (or (dgraph id) (throw (ex-info "BUG! Derivation not found" {:id id :derivations dgraph})))]
      (cond
        (or (integer? form) (boolean? form)) form
        (symbol? form) (if (bound? form)
                         form
                         (form-from-ssa* dgraph bound? form))
        (seq? form) (cond
                      (= 'get* (first form)) (list 'get* (form-from-ssa* dgraph bound? (second form)) (last form))
                      :else (apply list (first form) (map (partial form-from-ssa* dgraph bound?) (rest form))))
        (map? form) (-> form (dissoc :$type) (update-vals (partial form-from-ssa* dgraph bound?)) (assoc :$type (:$type form)))
        :else (throw (ex-info "BUG! Cannot reconstruct form from SSA representation" {:id id :form form :derivations dgraph}))))))

(s/defn ^:private guards-from-ssa :- {s/Any s/Any}
  "To facilitate testing"
  [dgraph :- Derivations, bound :- #{s/Symbol}, guards :- Guards]
  (-> guards
      (update-keys (partial form-from-ssa* dgraph bound))
      (update-vals
       (fn [guards]
         (if (seq guards)
           (->> guards
                (map #(->> % (map (partial form-from-ssa* dgraph bound)) (mk-junct 'and)))
                (mk-junct 'or))
           true)))))

(s/defn ^:private form-from-ssa
  [scope :- #{s/Symbol}, derivations :- Derivations id]
  (let [usage-counts (->> derivations vals (mapcat (comp referenced-derivations)) frequencies)
        ordering (zipmap (topo-sort derivations) (range))
        to-bind (->> derivations
                     (remove
                      (fn [[id [form htype]]]
                        (or (contains? scope form)
                            (integer? form)
                            (boolean? form)
                            (<= (get usage-counts id 0) 1))))
                     (map first)
                     set)]
    (cond->> (form-from-ssa* derivations (set/union to-bind scope) id)
      (seq to-bind)
      (list 'let
            (->> to-bind
                 (sort-by ordering)
                 (reduce
                  (fn [[bound-set bindings] id]
                    [(conj bound-set id)
                     (conj bindings id (form-from-ssa* derivations bound-set id))])
                  [scope []])
                 second
                 vec)))))

(s/defn ^:private spec-from-ssa :- halite-envs/SpecInfo
  [spec-info :- SpecInfo]
  ;; count usages of each derivation
  ;; a derivation goes into a top-level let iff
  ;;   it has multiple usages and is not a symbol/integer/boolean
  ;; the let form is orderd via topological sort
  ;; then we reconstitute the let bindings and constraints,
  ;; and assemble into the final form
  (let [{:keys [derivations constraints spec-vars] :as spec-info} (prune-derivations spec-info true)
        usage-counts (->> derivations vals (mapcat (comp referenced-derivations)) frequencies)
        ordering (zipmap (topo-sort derivations) (range))
        spec-var-syms (->> spec-vars keys (map symbol) set)
        to-bind (->> derivations
                     (remove
                      (fn [[id [form htype]]]
                        (or (contains? spec-var-syms form)
                            (integer? form)
                            (boolean? form)
                            (<= (get usage-counts id 0) 1))))
                     (map first)
                     set)
        bound (set/union to-bind spec-var-syms)
        bindings (->> to-bind
                      (sort-by ordering)
                      (reduce
                       (fn [[bound-set bindings] id]
                         [(conj bound-set id)
                          (conj bindings id (form-from-ssa* derivations bound-set id))])
                       [spec-var-syms []])
                      second
                      vec)
        constraint (->> constraints (map (comp (partial form-from-ssa* derivations bound) second)) (mk-junct 'and))
        constraint (cond->> constraint
                     (seq bindings) (list 'let bindings))]
    (-> spec-info
        (dissoc :derivations)
        (assoc :constraints [["$all" constraint]]))))

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
   (binding [*next-id* (atom 0)]
     (let [spec-id (:$type spec-bound)
           sctx (->> spec-id
                     (build-spec-ctx senv)
                     (fixpoint lower-instance-comparisons)
                     (fixpoint push-gets-into-ifs)
                     (lower-implicit-constraints)
                     (fixpoint cancel-get-of-instance-literal))]
       (->> spec-bound
            (spec-ify-bound sctx)
            (spec-from-ssa)
            (to-choco-spec))))))
