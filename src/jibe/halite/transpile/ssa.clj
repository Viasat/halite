;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.ssa
  "Rewrite halite specs such that their expressions are stored in a single directed graph
  that is reminiscent of the single static assignment (SSA) representation often used
  in compilers."
  (:require [clojure.set :as set]
            [jibe.halite.envs :as halite-envs]
            [jibe.halite.types :as halite-types]
            [jibe.halite.transpile.util :refer [mk-junct]]
            [schema.core :as s]
            [weavejester.dependency :as dep]))

(def ^:private renamed-ops
  '{mod* mod})

(def ^:private supported-halite-ops
  (into
   '#{dec inc + - * < <= > >= and or not => div mod expt abs = if not= let get*}
   (keys renamed-ops)))

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

(s/defn referenced-derivations :- [DerivationName]
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

(s/defn deref-id :- Derivation
  "Return the derivation in dgraph referenced by id, possibly
  indirectly."
  [dgraph :- Derivations id]
  (loop [[form :as d] (dgraph id)]
    (if (nil? (s/check DerivationName form))
      (recur (dgraph form))
      d)))

(s/defn reachable-derivations :- #{DerivationName}
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
  "A halite spec, but with all expressions encoded in a single SSA directed graph."
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

(s/defn negated :- DerivationName
  "Return the id of the node that represents the negation of the node with given id."
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

(defn- fresh-id! []
  (symbol (str "$" (swap! *next-id* inc))))

;;;;;;;;;;;;;;;; Converting to SSA ;;;;;;;;;;;;;;;;;;;;;;;

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

(s/defn form-to-ssa :- DerivResult
  "Add the SSA representation of form (an arbitrary halite expression) to the given directed graph,
  returning a tuple of the resulting graph and the id of the node for form.

  When replace-id is specified, replace the node with that id with the node for form. This is useful
  for rewriting expressions in place."
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

(s/defn constraint-to-ssa :- [(s/one Derivations :dgraph), [(s/one s/Str :cname) (s/one DerivationName :form)]]
  "TODO: Refactor me as add-constraint, taking and returning SpecInfo."
  [senv :- (s/protocol halite-envs/SpecEnv), tenv :- (s/protocol halite-envs/TypeEnv), derivations :- Derivations, [cname constraint-form]]
  (let [[derivations id ] (form-to-ssa {:senv senv :tenv tenv :env {} :dgraph derivations} constraint-form)]
    [derivations [cname id]]))

(s/defn spec-to-ssa :- SpecInfo
  "Convert a halite spec into an SSA-based representation."
  [senv :- (s/protocol halite-envs/SpecEnv), spec-info :- halite-envs/SpecInfo]
  (let [tenv (halite-envs/type-env-from-spec senv spec-info)
        [derivations constraints]
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
  "A map of spec ids to specs in SSA form."
  {halite-types/NamespacedKeyword SpecInfo})

(s/defn as-spec-env :- (s/protocol halite-envs/SpecEnv)
  "Adapt a SpecCtx to a SpecEnv."
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

(s/defn build-spec-ctx :- SpecCtx
  "Starting from root-spec-id and looking up specs from senv, build and return a SpecContext
  containing the root spec and every spec transitively referenced from it, in SSA form."
  [senv :- (s/protocol halite-envs/SpecEnv), root-spec-id :- halite-types/NamespacedKeyword]
  (-> root-spec-id
      (->> (reachable-specs senv))
      (update-vals (partial spec-to-ssa senv))))

;;;;;;;;;; Pruning unreachable expressions ;;;;;;;;;;;

;; Expression rewriting in the lowering passes may end up making expression nodes
;; unreachable from the 'roots' of the derivations graph. This shouldn't affect semantics,
;; but it may produce unnecessary let bindings when the graph is turned back into expressions.
;; To avoid this, we prune unreachable expressions from the graph after lowering phases that might
;; introduce them.
;; HOWEVER! When a form is put into SSA, (possibly unreachable) nodes representing the negation of
;; each boolean expression are added, and pruning will remove them! So, don't prune until you're "done".

(s/defn prune-dgraph :- Derivations
  "Prune nodes not reachable from the roots. When prune-negations? is true, negation nodes
  that are not actually used in the root expressions are also pruned; otherwise, they are not."
  [dgraph :- Derivations, roots :- #{DerivationName}, prune-negations? :- s/Bool]
  (let [reachable (reduce
                   (fn [reachable id]
                     (reachable-derivations dgraph (not prune-negations?) reachable id))
                   #{}
                   roots)]
    (->> dgraph
         (filter #(reachable (first %)))
         (into {}))))

(s/defn prune-derivations :- SpecInfo
  "Prune nodes not reachable from the roots. When prune-negations? is true, negation nodes
  that are not actually used in the root expressions are also pruned; otherwise, they are not."
  [{:keys [constraints] :as spec-info} :- SpecInfo, prune-negations? :- s/Bool]
  (let [roots (->> constraints (map second) set)]
    (update spec-info :derivations prune-dgraph roots prune-negations?)))

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

(s/defn form-from-ssa
  [scope :- #{s/Symbol}, derivations :- Derivations, id :- DerivationName]
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

(s/defn spec-from-ssa :- halite-envs/SpecInfo
  "Convert an SSA spec back into a regular halite spec."
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