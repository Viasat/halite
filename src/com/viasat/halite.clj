;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite
  "Expression language for resource spec constraints and refinements that is almost, but
  not quite, a drop-in replacement for salt."
  (:require [clojure.set :as set]
            [com.viasat.halite.h-err :as h-err]
            [com.viasat.halite.analysis :as halite-analysis]
            [com.viasat.halite.base :as halite-base]
            [com.viasat.halite.lint :as halite-lint]
            [com.viasat.halite.type-check :as halite-type-check]
            [com.viasat.halite.type-of :as halite-type-of]
            [com.viasat.halite.eval :as halite-eval]
            [com.viasat.halite.types :as halite-types]
            [com.viasat.halite.envs :as halite-envs]
            [com.viasat.halite.syntax-check :as halite-syntax-check]
            [com.viasat.halite.lib.format-errors :refer [throw-err with-exception-data]]
            [potemkin]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defn eval-predicate :- Boolean
  [ctx :- halite-eval/EvalContext
   tenv :- (s/protocol halite-envs/TypeEnv)
   bool-expr
   spec-id :- halite-types/NamespacedKeyword
   constraint-name :- (s/maybe String)]
  (with-exception-data {:form bool-expr
                        :spec-id spec-id
                        :constraint-name constraint-name}
    (halite-type-check/type-check-constraint-expr (:senv ctx) tenv bool-expr))
  (halite-eval/eval-predicate ctx tenv bool-expr spec-id constraint-name))

(s/defn eval-refinement :- (s/maybe s/Any)
  "Returns an instance of type spec-id, projected from the instance vars in ctx,
  or nil if the guards prevent this projection."
  [ctx :- halite-eval/EvalContext
   tenv :- (s/protocol halite-envs/TypeEnv)
   spec-id :- halite-types/NamespacedKeyword
   expr
   refinement-name :- (s/maybe String)]
  (if (contains? halite-eval/*refinements* spec-id)
    (halite-eval/*refinements* spec-id) ;; cache hit
    (do
      (with-exception-data {:form expr
                            :spec-id spec-id
                            :refinement-name refinement-name}
        (halite-type-check/type-check-refinement-expr (:senv ctx) tenv spec-id expr))
      (halite-eval/eval-refinement ctx tenv spec-id expr refinement-name))))

(s/defn ^:private load-env
  "Evaluate the contents of the env to get instances loaded with refinements."
  [senv :- (s/protocol halite-envs/SpecEnv)
   env :- (s/protocol halite-envs/Env)]
  (let [empty-env (halite-envs/env {})]
    ;; All runtime values are homoiconic. We eval them in an empty environment
    ;; to initialize refinements for all instances.
    (reduce
     (fn [env [k v]]
       (halite-envs/bind env k (halite-eval/eval-expr* {:env empty-env :senv senv} v)))
     empty-env
     (halite-envs/bindings env))))

(s/defn ^:private type-check-env
  "Type check the contents of the type environment."
  [senv :- (s/protocol halite-envs/SpecEnv)
   tenv :- (s/protocol halite-envs/TypeEnv)
   env :- (s/protocol halite-envs/Env)]
  (let [declared-symbols (set (keys (halite-envs/scope tenv)))
        bound-symbols (set (keys (halite-envs/bindings env)))
        unbound-symbols (set/difference declared-symbols bound-symbols)
        empty-env (halite-envs/env {})]
    (when (seq unbound-symbols)
      (throw-err (h-err/symbols-not-bound {:unbound-symbols unbound-symbols, :tenv tenv, :env env})))
    (doseq [sym declared-symbols]
      (let [declared-type (get (halite-envs/scope tenv) sym)
            ;; it is not necessary to setup the eval bindings for the following because the
            ;; instances have already been processed by load-env at this point
            value (halite-eval/eval-expr* {:env empty-env :senv senv} (get (halite-envs/bindings env) sym))
            actual-type (halite-type-of/type-of senv tenv value)]
        (when-not (halite-types/subtype? actual-type declared-type)
          (throw-err (h-err/value-of-wrong-type {:variable sym :value value :expected declared-type :actual actual-type})))))))

(defmacro optionally-with-eval-bindings [flag form]
  `(if ~flag
     (binding [halite-eval/*eval-predicate-fn* eval-predicate
               halite-eval/*eval-refinement-fn* eval-refinement]
       ~form)
     ~form))

(def default-eval-expr-options {:type-check-expr? true
                                :type-check-env? true
                                :type-check-spec-refinements-and-constraints? true
                                :check-for-spec-cycles? true})

(s/defn eval-expr :- s/Any
  "Evaluate a halite expression against the given type environment, and return the result. Optionally check the
  bindings in the environment are checked against the type environment before evaluation. Optionally
  type check the expression before evaluating it. Optionally type check any refinements or
  constraints involved with instance literals in the expr and env. By default all checks are
  performed."
  ([senv :- (s/protocol halite-envs/SpecEnv)
    tenv :- (s/protocol halite-envs/TypeEnv)
    env :- (s/protocol halite-envs/Env)
    expr]
   (eval-expr senv tenv env expr default-eval-expr-options))

  ([senv :- (s/protocol halite-envs/SpecEnv)
    tenv :- (s/protocol halite-envs/TypeEnv)
    env :- (s/protocol halite-envs/Env)
    expr
    options :- {(s/optional-key :type-check-expr?) Boolean
                (s/optional-key :type-check-env?) Boolean
                (s/optional-key :type-check-spec-refinements-and-constraints?) Boolean
                (s/optional-key :check-for-spec-cycles?) Boolean
                (s/optional-key :limits) halite-base/Limits}]
   (let [{:keys [type-check-expr? type-check-env? type-check-spec-refinements-and-constraints? check-for-spec-cycles?
                 limits]} options]
     (binding [halite-base/*limits* (or limits halite-base/*limits*)]
       (when check-for-spec-cycles?
         (when (not (map? senv))
           (throw-err (h-err/spec-map-needed {})))
         (when-let [cycle (halite-analysis/find-cycle-in-dependencies senv)]
           (throw-err (h-err/spec-cycle {:cycle cycle}))))
       (when type-check-expr?
         ;; it is not necessary to setup the eval bindings here because type-check does not invoke the
         ;; evaluator
         (halite-type-check/type-check senv tenv expr))
       (let [loaded-env (optionally-with-eval-bindings
                         type-check-spec-refinements-and-constraints?
                         (load-env senv env))]
         (when type-check-env?
           ;; it is not necessary to setup the eval bindings here because env values were checked by load-env
           (type-check-env senv tenv loaded-env))
         (optionally-with-eval-bindings
          type-check-spec-refinements-and-constraints?
          (halite-eval/eval-expr* {:env loaded-env :senv senv} expr)))))))

(defn syntax-check
  ([expr]
   (syntax-check expr {}))
  ([expr options]
   (let [{:keys [limits]} options]
     (binding [halite-base/*limits* (or limits halite-base/*limits*)]
       (halite-syntax-check/syntax-check expr)))))

(defn type-check-and-lint
  ([senv tenv expr]
   (type-check-and-lint senv tenv expr))
  ([senv tenv expr options]
   (let [{:keys [limits]} options]
     (binding [halite-base/*limits* (or limits halite-base/*limits*)]
       (halite-lint/type-check-and-lint senv tenv expr)))))

;;

(potemkin/import-vars
 [halite-type-check
  type-check type-check-spec type-check-refinement-expr])

(potemkin/import-vars
 [halite-syntax-check
  check-n])

(potemkin/import-vars
 [halite-base
  integer-or-long? fixed-decimal? check-count
  Limits])

(potemkin/import-vars
 [halite-base
  h< h> h<= h>= h+ h-])

(potemkin/import-vars
 [halite-envs
  primitive-types
  Refinement MandatoryVarType VarType NamedConstraint SpecVars RefinesTo SpecInfo
  halite-type-from-var-type
  SpecEnv lookup-spec spec-env
  TypeEnv type-env type-env-from-spec
  Env env env-from-inst
  SpecMap
  ;; more advanced
  maybe-type? no-maybe])

(potemkin/import-vars
 [halite-types
  HaliteType decimal-type vector-type set-type namespaced-keyword? abstract-spec-type concrete-spec-type
  nothing-like? join])
