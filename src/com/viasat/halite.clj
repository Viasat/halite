;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite
  "Expression language for resource spec constraints and refinements that is almost, but
  not quite, a drop-in replacement for salt."
  (:require [clojure.set :as set]
            [com.viasat.halite.analysis :as analysis]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.eval :as eval]
            [com.viasat.halite.h-err :as h-err]
            [com.viasat.halite.lib.format-errors :as format-errors]
            [com.viasat.halite.lint :as lint]
            [com.viasat.halite.type-check :as type-check]
            [com.viasat.halite.type-of :as type-of]
            [com.viasat.halite.types :as types]
            [com.viasat.halite.syntax-check :as syntax-check]
            [com.viasat.halite.var-types :as var-types]
            [potemkin]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(def ^:dynamic *debug* false)

(s/defn ^:private eval-predicate :- Boolean
  [ctx :- eval/EvalContext
   tenv :- (s/protocol envs/TypeEnv)
   bool-expr
   spec-id :- types/NamespacedKeyword
   constraint-name :- (s/maybe base/ConstraintName)]
  (format-errors/with-exception-data {:form bool-expr
                                      :spec-id spec-id
                                      :constraint-name (name constraint-name)}
    (type-check/type-check-constraint-expr (:senv ctx) tenv bool-expr))
  (eval/eval-predicate ctx tenv bool-expr spec-id constraint-name))

(s/defn ^:private eval-refinement :- (s/maybe s/Any)
  "Returns an instance of type spec-id, projected from the instance vars in ctx,
  or nil if the guards prevent this projection."
  [ctx :- eval/EvalContext
   tenv :- (s/protocol envs/TypeEnv)
   spec-id :- types/NamespacedKeyword
   expr
   refinement-name :- (s/maybe String)]
  (if (contains? eval/*refinements* spec-id)
    (eval/*refinements* spec-id) ;; cache hit
    (do
      (format-errors/with-exception-data {:form expr
                                          :spec-id spec-id
                                          :refinement-name refinement-name}
        (type-check/type-check-refinement-expr (:senv ctx) tenv spec-id expr))
      (eval/eval-refinement ctx tenv spec-id expr refinement-name))))

(s/defn ^:private load-env
  "Evaluate the contents of the env to get instances loaded with refinements."
  [senv :- (s/protocol envs/SpecEnv)
   env :- (s/protocol envs/Env)]
  (let [empty-env (envs/env {})]
    ;; All runtime values are homoiconic. We eval them in an empty environment
    ;; to initialize refinements for all instances.
    (reduce
     (fn [env [k v]]
       (envs/bind env k (eval/eval-expr* {:env empty-env :senv senv} v)))
     empty-env
     (envs/bindings env))))

(s/defn ^:private type-check-env
  "Type check the contents of the type environment."
  [senv :- (s/protocol envs/SpecEnv)
   tenv :- (s/protocol envs/TypeEnv)
   env :- (s/protocol envs/Env)]
  (let [declared-symbols (envs/tenv-keys tenv)
        bound-symbols (set (keys (envs/bindings env)))
        unbound-symbols (set/difference declared-symbols bound-symbols)
        empty-env (envs/env {})]
    (when (seq unbound-symbols)
      (format-errors/throw-err (h-err/symbols-not-bound {:unbound-symbols unbound-symbols, :tenv tenv, :env env})))
    (doseq [sym declared-symbols]
      (let [declared-type (envs/lookup-type* tenv sym)
            ;; it is not necessary to setup the eval bindings for the following because the
            ;; instances have already been processed by load-env at this point
            value (eval/eval-expr* {:env empty-env :senv senv} (get (envs/bindings env) sym))
            actual-type (type-of/type-of senv tenv value)]
        (when-not (types/subtype? actual-type declared-type)
          (format-errors/throw-err (h-err/value-of-wrong-type {:variable sym :value value :expected declared-type :actual actual-type})))))))

(defmacro optionally-with-eval-bindings [flag form]
  `(if ~flag
     (binding [eval/*eval-predicate-fn* #'eval-predicate
               eval/*eval-refinement-fn* #'eval-refinement]
       ~form)
     ~form))

(def default-eval-expr-options {:type-check-expr? true
                                :type-check-env? true
                                :type-check-spec-refinements-and-constraints? true
                                :check-for-spec-cycles? true})

(s/defn eval-expr :- s/Any
  "Evaluate a halite expression against the given type environment, and return the
  result. Optionally check the bindings in the environment against the type environment before
  evaluation. Optionally type check the expression before evaluating it. Optionally type check any
  refinements or constraints involved with instance literals in the expr and env. By default all
  checks are performed. Using the variant where 'options' are passed in requires the caller to turn
  on all of the checks that are needed. If a check has not already been performed and is not turned
  on when 'options' are passed in, then the results are indeterminate."
  ([senv :- (s/protocol envs/SpecEnv)
    tenv :- (s/protocol envs/TypeEnv)
    env :- (s/protocol envs/Env)
    expr]
   (eval-expr senv tenv env expr default-eval-expr-options))

  ([senv :- (s/protocol envs/SpecEnv)
    tenv :- (s/protocol envs/TypeEnv)
    env :- (s/protocol envs/Env)
    expr
    options :- {(s/optional-key :type-check-expr?) Boolean
                (s/optional-key :type-check-env?) Boolean
                (s/optional-key :type-check-spec-refinements-and-constraints?) Boolean
                (s/optional-key :check-for-spec-cycles?) Boolean
                (s/optional-key :limits) base/Limits}]
   (let [senv (var-types/to-halite-spec-env senv)
         {:keys [type-check-expr? type-check-env? type-check-spec-refinements-and-constraints? check-for-spec-cycles?
                 limits]} options]
     (binding [base/*limits* (or limits base/*limits*)]
       (when check-for-spec-cycles?
         (when (not (map? senv))
           (format-errors/throw-err (h-err/spec-map-needed {})))
         (when-let [cycle (analysis/find-cycle-in-dependencies senv)]
           (format-errors/throw-err (h-err/spec-cycle {:cycle cycle}))))
       (when type-check-expr?
         ;; it is not necessary to setup the eval bindings here because type-check does not invoke the
         ;; evaluator
         (type-check/type-check senv tenv expr))
       (let [loaded-env (optionally-with-eval-bindings
                         type-check-spec-refinements-and-constraints?
                         (load-env senv env))]
         (when type-check-env?
           ;; it is not necessary to setup the eval bindings here because env values were checked by load-env
           (type-check-env senv tenv loaded-env))
         (optionally-with-eval-bindings
          type-check-spec-refinements-and-constraints?
          (binding [eval/*debug* *debug*]
            (eval/eval-expr* {:env loaded-env :senv senv} expr))))))))

(defn syntax-check
  ([expr]
   (syntax-check expr {}))
  ([expr options]
   (let [{:keys [limits]} options]
     (binding [base/*limits* (or limits base/*limits*)]
       (syntax-check/syntax-check expr)))))

(defn type-check-and-lint
  ([senv tenv expr]
   (type-check-and-lint senv tenv expr default-eval-expr-options))
  ([senv tenv expr options]
   (let [senv (var-types/to-halite-spec-env senv)
         {:keys [limits]} options]
     (binding [base/*limits* (or limits base/*limits*)]
       (lint/type-check-and-lint senv tenv expr)))))

(defn type-check
  [senv & args]
  (let [senv (var-types/to-halite-spec-env senv)]
    (apply type-check/type-check senv args)))

(defn type-check-spec
  [senv spec-info]
  (let [senv (var-types/to-halite-spec-env senv)
        spec-info (var-types/to-halite-spec senv spec-info)]
    (type-check/type-check-spec senv spec-info)))

(defn type-check-refinement-expr
  [senv & args]
  (let [senv (var-types/to-halite-spec-env senv)]
    (apply type-check/type-check-refinement-expr senv args)))

(s/defn lookup-spec :- (s/maybe var-types/UserSpecInfo)
  "Look up the spec with the given id in the given type environment, returning variable type information.
  Returns nil when the spec is not found."
  [senv :- (s/protocol envs/SpecEnv)
   spec-id :- types/NamespacedKeyword]
  (envs/lookup-spec* senv spec-id))

(s/defn spec-env :- (s/protocol envs/SpecEnv)
  [spec-info-map :- {types/NamespacedKeyword var-types/UserSpecInfo}]
  (var-types/halite-spec-env spec-info-map))

(s/defn type-env-from-spec :- (s/protocol envs/TypeEnv)
  "Return a type environment where spec lookups are delegated to tenv, but the in-scope symbols
  are the variables of the given resource spec."
  [senv :- (s/protocol envs/SpecEnv)
   spec :- var-types/UserSpecInfo]
  (let [spec (var-types/to-halite-spec senv spec)]
    (envs/type-env-from-spec spec)))

(s/defn env-from-inst :- (s/protocol envs/Env)
  [spec-info :- var-types/UserSpecInfo
   inst]
  (envs/env-from-field-keys (keys (:fields spec-info)) inst))

(s/defn vector-type :- var-types/VarType
  "Construct a type representing vectors of the given type."
  [elem-type :- var-types/VarType]
  [:Vec elem-type])

(s/defn set-type :- var-types/VarType
  "Construct a type representing sets of the given type."
  [elem-type :- var-types/VarType]
  [:Set elem-type])

(s/defn vector-type? :- Boolean
  [t :- var-types/VarType]
  (->> t
       var-types/no-maybe
       (#(and (vector? %)
              (= (first %) :Vec)))
       boolean))

(s/defn set-type? :- Boolean
  [t :- var-types/VarType]
  (->> t
       var-types/no-maybe
       (#(and (vector? %)
              (= (first %) :Set)))
       boolean))

(s/defn halite-vector-type :- types/HaliteType
  "Construct a type representing vectors of the given type."
  [elem-type :- types/HaliteType]
  (types/vector-type elem-type))

(s/defn halite-set-type :- types/HaliteType
  "Construct a type representing sets of the given type."
  [elem-type :- types/HaliteType]
  (types/set-type elem-type))

(s/defn halite-elem-type :- (s/maybe types/HaliteType)
  [t]
  (types/elem-type t))

;;

(potemkin/import-vars
 [syntax-check ;; this is a namespace name, not a function name
  check-n])

(potemkin/import-vars
 [base
  integer-or-long? fixed-decimal? check-count
  Limits])

(potemkin/import-vars
 [base
  h< h> h<= h>= h+ h-])

(potemkin/import-vars
 [envs
  Refinement RefinesTo Constraint
  SpecEnv
  TypeEnv type-env extend-scope
  Env env env-from-inst])

(potemkin/import-vars
 [types
  primitive-types
  HaliteType decimal-type decimal-type? decimal-scale namespaced-keyword? abstract-spec-type concrete-spec-type
  halite-set-type? halite-vector-type?
  nothing-like? join])

(potemkin/import-vars
 [var-types
  elem-type
  halite-type-from-var-type
  VarType UserSpecVars UserSpecInfo UserSpecMap
  ;; more advanced
  maybe-type? no-maybe])
