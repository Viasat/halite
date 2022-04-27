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

(declare Assignment)

(s/defschema AssignmentVal
  (s/cond-pre
   s/Int
   s/Bool
   #_(s/recursive #'Assignment)))

(s/defschema Assignment
  {:$type halite-types/NamespacedKeyword
   halite-types/BareKeyword AssignmentVal})

;;;;;;;;;;;;;;;; SSA Pass ;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private choco-ops
  '#{dec inc + - * < <= > >= and or not => div mod expt abs = if not= let})

(def ^:private supported-halite-ops
  (into choco-ops '#{get* #_refine-to}))

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
  [[form htype :as deriv] :- Derivation]
  (cond
    (seq? form) (->> form rest (filter symbol?))
    (map? form) (-> form (dissoc :$type) vals)
    ;; this is a bit of a hack
    (nil? (s/check DerivationName form)) [form]
    :else []))

(s/defschema Derivations
  {DerivationName Derivation})

(s/defn ^:private reachable-derivations :- #{DerivationName}
  ([dgraph :- Derivations, id :- DerivationName]
   (reachable-derivations dgraph #{} id))
  ([dgraph :- Derivations, reached :- #{DerivationName}, id :- DerivationName]
   (loop [[next-id & ids] [id]
          reached reached]
     (cond
       (nil? next-id) reached
       (reached next-id) (recur ids reached)
       :else (recur (into ids (referenced-derivations (dgraph next-id))) (conj reached next-id))))))

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
  (add-derivation
   dgraph
   [form
    (cond
      ('#{+ - * div mod expt abs} op) :Integer
      ('#{< <= > >= and or not => = not=} op) :Boolean
      :else (throw (ex-info (format  "BUG! Couldn't determine type of function application for '%s'" op)
                            {:form form})))]))

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

(s/defn ^:private push-gets-into-ifs-in-spec :- SpecInfo
  [sctx :- SpecCtx, {:keys [derivations] :as spec-info} :- SpecInfo]
  ;; TODO: Make this work in the presence of composition
  (let [senv (as-spec-env sctx)
        tenv (halite-envs/type-env-from-spec senv (dissoc spec-info :derivations))
        ctx {:senv senv :tenv tenv :env {} :dgraph derivations}]
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
         (assoc spec-info :derivations))))

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
     {}
     (->> dg (dep/topo-sort) (remove #(= :nothing %))))))

(s/defn ^:private lower-instance-literals-in-spec :- SpecInfo
  "Remove instance literals entirely. Assumes the only instance-valued expressions are
  instance literals and get* forms. 'Inlines' chains of get* forms. All instance literals should
  thus be unreferenced, and are pruned."
  [spec-info :- SpecInfo]
  ;; double-check that all instance-valued expressions are literals or get* forms.
  (doseq [[form htype] (->> spec-info :derivations vals)]
    (when-not (#{:Integer :Boolean} htype)
      (when-not (halite-types/spec-type? htype)
        (throw (ex-info (format "BUG! lower-instance-literals-in-spec assumes no expressions of type '%s'" htype)
                        {:form form :type htype})))
      (when-not (or (map? form) (and (seq? form) (= 'get* (first form))))
        (throw (ex-info "BUG! lower-instance-literals-in-spec assumes all instance-valued expressions are literals or get* forms"
                        {:form form})))))
  ;; inline all primitive-valued get* forms, following chains when necessary
  ;; (get* (get* {:$foo :bar {:$bar :x 12}} :bar) :x)
  ;; $1 12
  ;; $2 {:$bar :x $1}
  ;; $3 {:$foo :bar $2}
  ;; $4 (get* $3 :bar)
  ;; $5 (get* $4 :x)
  (let [id-to-inline (fn [dgraph id stack]
                       (if (empty? stack)
                         id
                         (let [[form htype] (dgraph id)]
                           (cond
                             (map? form) (recur dgraph (get form (peek stack)) (pop stack))
                             (and (seq? form) (= 'get* (first form))) (recur dgraph (second form) (conj stack (last form)))
                             :else (throw (ex-info "BUG! Unexpected expression while lowering instance literals"
                                                   {:form form :type htype :stack stack}))))))]
    (->> spec-info
         :derivations
         (filter (fn [[id [form htype]]]
                   (and (seq? form) (= 'get* (first form)) (#{:Integer :Boolean} htype))))
         (reduce
          (fn [dgraph [id [form htype]]]
            (assoc dgraph id [(id-to-inline dgraph (second form) [(last form)]) htype]))
          (:derivations spec-info))
         (assoc spec-info :derivations))
    )
  )

;;;;;;;;;; Pruning unreachable expressions ;;;;;;;;;;;

;; Expression rewriting in the lowering passes may end up making expression nodes
;; unreachable from the 'roots' of the derivations graph. This shouldn't affect semantics,
;; but it may produce unnecessary let bindings when the graph is turned back into expressions.
;; To avoid this, we prune unreachable expressions from the graph after lowering phases that might
;; introduce them.
;; HOWEVER! When a form is put into SSA, (possibly unreachable) nodes representing the negation of
;; each boolean expression are added, and pruning will remove them! So, don't prune until you're "done".

(s/defn ^:private prune-derivations :- SpecInfo
  [{:keys [derivations constraints] :as spec-info} :- SpecInfo]
  (let [reachable (reduce
                   (fn [reachable [cname id]]
                     (reachable-derivations derivations reachable id))
                   #{}
                   constraints)]
    (->> derivations
         (filter #(reachable (first %)))
         (into {})
         (assoc spec-info :derivations))))

;;;;;;;;; Assignment Spec-ification ;;;;;;;;;;;;;;;;

;; TODO: Composition, but only after instance literal lowering
;; We want to handle recursive specifications right off the bat.

(s/defn ^:private spec-ify-assignment :- SpecInfo
  [sctx :- SpecCtx, assignment :- Assignment]
  (let [{:keys [spec-vars constraints refines-to derivations] :as spec-info} (-> assignment :$type sctx prune-derivations)]
    (doseq [[var-sym htype] spec-vars]
      (when-not (#{:Integer :Boolean} htype)
        (throw (ex-info (format "BUG! Assignments of type '%' not supported yet" htype)
                        {:var-sym var-sym :type htype}))))
    (when (seq refines-to)
      (throw (ex-info (format "BUG! Cannot spec-ify refinements") {:spec-info spec-info})))
    (let [tenv (halite-envs/type-env-from-spec (as-spec-env sctx) (dissoc spec-info :derivations))]
      (->> (dissoc assignment :$type)
           (sort-by key)
           (map (fn [[var-kw v]] [(str "$" (name var-kw)) (list '= (symbol var-kw) v)]))
           (reduce
            (fn [{:keys [derivations] :as spec-info} constraint]
              (let [[derivations constraint] (constraint-to-ssa (as-spec-env sctx) tenv derivations constraint)]
                (-> spec-info
                    (assoc :derivations derivations)
                    (update :constraints conj constraint))))
            {:spec-vars spec-vars
             :constraints constraints
             :derivations derivations
             :refines-to {}})))))

;;;;;;;;; Converting from SSA back into a more readable form ;;;;;;;;

(s/defn ^:private topo-sort :- [DerivationName]
  [derivations :- Derivations]
  (->> derivations
       (reduce
        (fn [g [id d]]
          (reduce #(dep/depend %1 id %2) g (referenced-derivations d)))
        (dep/graph))
       (dep/topo-sort)))

(s/defn ^:private form-from-ssa
  [dgraph :- Derivations, bound? :- #{s/Symbol} id]
  (if (bound? id)
    id
    (let [[form _] (or (dgraph id) (throw (ex-info "BUG! Derivation not found" {:id id :derivations dgraph})))]
      (cond
        (or (integer? form) (boolean? form)) form
        (symbol? form) (if (bound? form)
                         form
                         (form-from-ssa dgraph bound? form))
        (seq? form) (cond
                      (= 'get* (first form)) (list 'get* (form-from-ssa dgraph bound? (second form)) (last form))
                      :else (apply list (first form) (map (partial form-from-ssa dgraph bound?) (rest form))))
        (map? form) (-> form (dissoc :$type) (update-vals (partial form-from-ssa dgraph bound?)) (assoc :$type (:$type form)))
        :else (throw (ex-info "BUG! Cannot reconstruct form from SSA representation" {:id id :form form :derivations dgraph}))))))

(s/defn ^:private guards-from-ssa :- {s/Any s/Any}
  "To facilitate testing"
  [dgraph :- Derivations, bound :- #{s/Symbol}, guards :- Guards]
  (-> guards
      (update-keys (partial form-from-ssa dgraph bound))
      (update-vals
       (fn [guards]
         (if (seq guards)
           (->> guards
                (map #(->> % (map (partial form-from-ssa dgraph bound)) (mk-junct 'and)))
                (mk-junct 'or))
           true)))))

(s/defn ^:private spec-from-ssa :- halite-envs/SpecInfo
  [spec-info :- SpecInfo]
  ;; count usages of each derivation
  ;; a derivation goes into a top-level let iff
  ;;   it has multiple usages and is not a symbol/integer/boolean
  ;; the let form is orderd via topological sort
  ;; then we reconstitute the let bindings and constraints,
  ;; and assemble into the final form
  (let [{:keys [derivations constraints spec-vars] :as spec-info} (prune-derivations spec-info)
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
                          (conj bindings id (form-from-ssa derivations bound-set id))])
                       [spec-var-syms []])
                      second
                      vec)
        constraint (->> constraints (map (comp (partial form-from-ssa derivations bound) second)) (mk-junct 'and))
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
  "Transpile the assignment into a choco-clj spec that encodes constraints that must
  hold for any valid completion of the assignment, according to the specification.
  The choco-clj spec will describe conditions that are necessary for validity, but may not describe
  conditions that are sufficient to guarantee validity."
  ([senv :- (s/protocol halite-envs/SpecEnv), assignment :- Assignment]
   (transpile senv assignment default-options))
  ([senv :- (s/protocol halite-envs/SpecEnv), assignment :- Assignment, opts :- Opts]
   (binding [*next-id* (atom 0)]
     (let [spec-id (:$type assignment)
           sctx (->> spec-id
                     (build-spec-ctx senv)
                     (lower-instance-comparisons)
                     (push-gets-into-ifs)
                     (lower-implicit-constraints))]
       (->> assignment
            (spec-ify-assignment sctx)
            (lower-instance-literals-in-spec)
            (spec-from-ssa)
            (to-choco-spec))))))
