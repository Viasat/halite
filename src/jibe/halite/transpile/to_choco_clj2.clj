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
  {DerivationName Derivation
   ;; this representation has been really frustrating to work with, I want to refactor it
   (s/optional-key :next-id) s/Int})

(s/defschema SpecInfo
  (assoc halite-envs/SpecInfo
         (s/optional-key :derivations) Derivations
         :constraints [[(s/one s/Str :cname) (s/one DerivationName :deriv)]]))

(s/defschema SpecCtx
  {halite-types/NamespacedKeyword SpecInfo})

(s/defschema DerivResult [(s/one Derivations :derivs) (s/one DerivationName :id)])

(s/defn ^:private find-form :- (s/maybe DerivationName)
  [dgraph :- Derivations, ssa-form :- SSAForm]
  (loop [[[id [form _] :as entry] & entries] (dissoc dgraph :next-id)]
    (when entry
      (if (= form ssa-form)
        id
        (recur entries)))))

(s/defn ^:private add-derivation :- DerivResult
  [dgraph :- Derivations, [ssa-form htype :as d] :- Derivation]
  (if-let [id (find-form dgraph ssa-form)]
    (if (not= htype (get-in dgraph [id 1]))
      (throw (ex-info (format "BUG! Tried to add derivation %s, but that form already recorded as %s" d (dgraph id))
                      {:derivation d :dgraph dgraph}))
      [dgraph id])
    (let [id (symbol (str "$" (:next-id dgraph)))]
      [(-> dgraph (assoc id d) (update :next-id inc)) id])))

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

(s/defn ^:private let-to-ssa :- DerivResult
  [tenv :- (s/protocol halite-envs/TypeEnv), env :- {s/Symbol DerivationName}, dgraph :- Derivations, [_ bindings body :as form]]
  (let [[tenv env dgraph] (reduce
                           (fn [[tenv env dgraph] [var-sym subexpr]]
                             (let [[dgraph id] (form-to-ssa tenv env dgraph subexpr)
                                   htype (get-in dgraph [id 1])]
                               [(halite-envs/extend-scope tenv var-sym htype)
                                (assoc env var-sym id)
                                dgraph]))
                           [tenv env dgraph]
                           (partition 2 bindings))]
    (form-to-ssa tenv env dgraph body)))

(s/defn ^:private app-to-ssa :- DerivResult
  [tenv :- (s/protocol halite-envs/TypeEnv), env :- {s/Symbol DerivationName}, dgraph :- Derivations, [op & args :as form]]
  (let [[dgraph args] (reduce (fn [[dgraph args] arg]
                                (let [[dgraph id] (form-to-ssa tenv env dgraph arg)]
                                  [dgraph (conj args id)]))
                              [dgraph []]
                              args)]
    (add-derivation-for-app dgraph (apply list op args))))

(s/defn ^:private symbol-to-ssa :- DerivResult
  [tenv :- (s/protocol halite-envs/TypeEnv), env :- {s/Symbol DerivationName}, dgraph :- Derivations, form]
  (cond
    (contains? dgraph form) [dgraph form]
    (contains? env form) [dgraph (env form)]
    :else (let [htype (or (get (halite-envs/scope tenv) form)
                          (throw (ex-info (format "BUG! Undefined: '%s'" form) {:tenv tenv :form form})))]
            (add-derivation dgraph [form htype]))))

(s/defn ^:private if-to-ssa :- DerivResult
  [tenv :- (s/protocol halite-envs/TypeEnv), env :- {s/Symbol DerivationName}, dgraph :- Derivations, [_ pred then else :as form]]
  (let [[dgraph pred-id] (form-to-ssa tenv env dgraph pred)
        [dgraph then-id] (form-to-ssa tenv env dgraph then)
        [dgraph else-id] (form-to-ssa tenv env dgraph else)
        htype (halite-types/meet (get-in dgraph [then-id 1]) (get-in dgraph [else-id 1]))]
    (add-derivation dgraph [(list 'if pred-id then-id else-id) htype])))

(s/defn ^:private form-to-ssa :- DerivResult
  [tenv :- (s/protocol halite-envs/TypeEnv), env :- {s/Symbol DerivationName}, dgraph :- Derivations, form]
  (cond
    (int? form) (add-derivation dgraph [form :Integer])
    (boolean? form) (add-derivation dgraph [form :Boolean])
    (symbol? form) (symbol-to-ssa tenv env dgraph form)
    (seq? form) (let [[op & args] form]
                  (when-not (contains? supported-halite-ops op)
                    (throw (ex-info (format "BUG! Cannot transpile operation '%s'" op) {:form form})))
                  (condp = op
                    'let (let-to-ssa tenv env dgraph form)
                    'if (if-to-ssa tenv env dgraph form)
                    (app-to-ssa tenv env dgraph form)))
    :else (throw (ex-info "BUG! Unsupported feature in halite->choco-clj transpilation"
                          {:form form}))))

(s/defn ^:private constraint-to-ssa :- [(s/one Derivations :dgraph), [(s/one s/Str :cname) (s/one DerivationName :form)]]
  [tenv :- (s/protocol halite-envs/TypeEnv), derivations :- Derivations, [cname constraint-form]]
  (let [[derivations id ] (form-to-ssa tenv {} derivations constraint-form)]
    [derivations [cname id]]))

(s/defn ^:private to-ssa :- SpecInfo
  [tenv :- (s/protocol halite-envs/TypeEnv), spec-info :- halite-envs/SpecInfo]
  (let [[derivations constraints]
        ,,(reduce
           (fn [[derivations constraints] constraint]
             (let [[derivations constraint] (constraint-to-ssa tenv derivations constraint)]
               [derivations (conj constraints constraint)]))
           [{:next-id 1} []]
           (:constraints spec-info))]
    (assoc spec-info
           :derivations (dissoc derivations :next-id)
           :constraints constraints)))

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

(s/defn ^:private from-ssa :- halite-envs/SpecInfo
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

(declare Assignment)

(s/defschema AssignmentVal
  (s/cond-pre
   s/Int
   s/Bool
   (s/recursive #'Assignment)))

(s/defschema Assignment
  {:$type halite-types/NamespacedKeyword
   halite-types/BareKeyword AssignmentVal})

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
   (let [spec-id (:$type assignment)
         spec-info (halite-envs/lookup-spec senv spec-id)
         tenv (halite-envs/type-env-from-spec senv spec-info)]
     (->> spec-info
          (to-ssa tenv)
          (from-ssa)
          (to-choco-spec))
     )))
