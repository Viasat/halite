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
  (into choco-ops '#{#_get* #_refine-to}))

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
   ;;{s/Keyword SSATerm}
   [(s/one SSAOp :op) DerivationName]))

(s/defschema Derivation
  [(s/one SSAForm :form) (s/one halite-types/HaliteType :type)])

(s/defn ^:private referenced-derivations :- [DerivationName]
  [[form htype :as deriv] :- Derivation]
  (cond
    (seq? form) (rest form)
    :else []))

(s/defschema Derivations
  {DerivationName Derivation})

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

(def ^:dynamic *next-id*
  "During transpilation, holds an atom whose value is the next globally unique derivation id."
  nil)

(s/defn ^:private add-derivation :- DerivResult
  [dgraph :- Derivations, [ssa-form htype :as d] :- Derivation]
  (if-let [id (find-form dgraph ssa-form)]
    (if (not= htype (get-in dgraph [id 1]))
      (throw (ex-info (format "BUG! Tried to add derivation %s, but that form already recorded as %s" d (dgraph id))
                      {:derivation d :dgraph dgraph}))
      [dgraph id])
    (let [id (symbol (str "$" (swap! *next-id* inc)))]
      [(assoc dgraph id d) id])))

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

(s/defn ^:private form-to-ssa :- DerivResult
  [{:keys [dgraph] :as ctx} :- SSACtx, form]
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
                    (app-to-ssa ctx form)))
    :else (throw (ex-info "BUG! Unsupported feature in halite->choco-clj transpilation"
                          {:form form}))))

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

(defn- spec-refs [{:keys [spec-vars refines-to] :as spec-info}]
  (->> spec-vars vals (map spec-ref-from-type) (remove nil?) (concat (keys refines-to))))

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

;;;;;;;;; Assignment Spec-ification ;;;;;;;;;;;;;;;;

;; TODO: Composition, but only after instance literal lowering
;; We want to handle recursive specifications right off the bat.

(s/defn ^:private spec-ify-assignment :- SpecInfo
  [sctx :- SpecCtx, assignment :- Assignment]
  (let [{:keys [spec-vars constraints refines-to derivations] :as spec-info} (sctx (:$type assignment))]
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

;;;;;;;;; Instance Comparison Lowering ;;;;;;;;

;; TODO Next!

;; * Support instance literals, get* in SSA pass.
;; * Replace all (dis-)equality nodes with instance-valued inputs.

;;;;;;;;; Instance Literal Lowering ;;;;;;;;;;;

;; Eliminate all instance literals, inlining implied constraints.
;; Note that a spec with a constraint containing an instance literal of that spec
;; would be self-referential in a way that I don't know how to handle.

;; * Constraint inlining
;; * Replace instance literal expression with sub-expressions, and
;;   all get* forms that reference the replaced instance literals with their sub-expressions.

;;;;;;;;; Converting from SSA back into a more readable form ;;;;;;;;

(s/defn ^:private topo-sort :- [DerivationName]
  [derivations :- Derivations]
  (->> derivations
       (reduce
        (fn [g [id d]]
          (reduce #(dep/depend %1 id %2) g (referenced-derivations d)))
        (dep/graph))
       (dep/topo-sort)))

(s/defn ^:private mk-junct :- s/Any
  [op :- (s/enum 'and 'or), clauses :- [s/Any]]
  (condp = (count clauses)
    0 ({'and true, 'or false} op)
    1 (first clauses)
    (apply list op clauses)))

(s/defn ^:private form-from-ssa
  [dgraph :- Derivations, bound? :- #{s/Symbol} id]
  (if (bound? id)
    id
    (let [[form _] (or (dgraph id) (throw (ex-info "BUG! Derivation not found" {:id id :derivations dgraph})))]
      (cond
        (or (integer? form) (boolean? form) (symbol? form)) form
        (seq? form) (apply list (first form) (map (partial form-from-ssa dgraph bound?) (rest form)))))))

(s/defn ^:private spec-from-ssa :- halite-envs/SpecInfo
  [{:keys [derivations constraints spec-vars] :as spec-info} :- SpecInfo]
  ;; count usages of each derivation
  ;; a derivation goes into a top-level let iff
  ;;   it has multiple usages and is not a symbol/integer/boolean
  ;; the let form is orderd via topological sort
  ;; then we reconstitute the let bindings and constraints,
  ;; and assemble into the final form
  (let [usage-counts (->> derivations vals (mapcat (comp referenced-derivations)) frequencies)
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
                       [#{} []])
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
                     ;; lowering phases go here
                     )]
       (->> assignment
            (spec-ify-assignment sctx)
            (spec-from-ssa)
            (to-choco-spec))))))
