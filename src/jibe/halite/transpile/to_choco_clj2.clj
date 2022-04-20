;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.transpile.to-choco-clj2
  "Another attempt at transpiling halite to choco-clj."
  (:require [jibe.halite.envs :as halite-envs]
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

(s/defschema SSAForm
  (s/cond-pre
   SSATerm
   ;;#{SSATerm}
   ;;{s/Keyword SSATerm}
   [(s/cond-pre SSATerm s/Keyword)]))

(s/defschema Derivation
  [(s/one SSAForm :form) (s/one halite-types/HaliteType :type)])

(s/defschema Derivations
  {:next-id s/Int
   DerivationName Derivation})

(s/defschema SpecInfo
  (assoc halite-envs/SpecInfo
         (s/optional-key :derivations) Derivations))

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

(s/defn ^:private constraint-to-ssa :- SpecInfo
  [tenv :- (s/protocol halite-envs/TypeEnv), {:keys [derivations] :as spec-info} :- SpecInfo [cname constraint-form]]
  (let [[derivations id] (form-to-ssa tenv {} derivations constraint-form)]
    (-> spec-info
        (assoc :derivations derivations)
        (update :constraints conj [cname id]))))

(s/defn ^:private to-ssa :- SpecInfo
  [tenv :- (s/protocol halite-envs/TypeEnv), spec-info :- halite-envs/SpecInfo]
  (reduce
   (partial constraint-to-ssa tenv)
   (assoc spec-info
          :derivations {:next-id 1}
          :constraints [])
   (:constraints spec-info)))


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
   ;; SSA pass
   ;; make choco model
   (throw (ex-info "Not implemented" {}))))
