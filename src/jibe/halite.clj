;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite
  "Expression language for resource spec constraints and refinements that is almost, but
  not quite, a drop-in replacement for salt."
  (:require [clojure.set :as set]
            [jibe.h-err :as h-err]
            [jibe.halite-base :as halite-base]
            [jibe.halite-type-check :as halite-type-check]
            [jibe.halite-eval :as halite-eval]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.halite-syntax-check :as halite-syntax-check]
            [jibe.lib.format-errors :refer [throw-err with-exception-data]]
            [potemkin]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defn eval-predicate :- Boolean
  [ctx :- halite-eval/EvalContext
   tenv :- (s/protocol halite-envs/TypeEnv)
   bool-expr
   constraint-name :- (s/maybe String)]
  (with-exception-data {:form bool-expr
                        :constraint-name constraint-name}
    (halite-type-check/type-check-constraint-expr (:senv ctx) tenv bool-expr))
  (halite-eval/eval-predicate ctx tenv bool-expr constraint-name))

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
      (halite-type-check/type-check-refinement-expr (:senv ctx) tenv spec-id expr)
      (halite-eval/eval-refinement ctx tenv spec-id expr refinement-name))))

(defmacro with-eval-bindings [form]
  `(binding [halite-eval/*eval-predicate-fn* eval-predicate
             halite-eval/*eval-refinement-fn* eval-refinement]
     ~form))

(defmacro optionally-with-eval-bindings [flag form]
  `(if ~flag
     (with-eval-bindings
       ~form)
     ~form))

(s/defn ^:private eval-expr-general :- s/Any
  [type-check? :- Boolean
   senv :- (s/protocol halite-envs/SpecEnv), tenv :- (s/protocol halite-envs/TypeEnv), env :- (s/protocol halite-envs/Env), expr]
  (when type-check?
    (with-eval-bindings
      (halite-type-check/type-check senv tenv expr)))
  (let [declared-symbols (set (keys (halite-envs/scope tenv)))
        bound-symbols (set (keys (halite-envs/bindings env)))
        unbound-symbols (set/difference declared-symbols bound-symbols)
        empty-env (halite-envs/env {})
        ;; All runtime values are homoiconic. We eval them in an empty environment
        ;; to initialize refinements for all instances.
        env (reduce
             (fn [env [k v]]
               (halite-envs/bind env k
                                 (optionally-with-eval-bindings type-check?
                                                                (halite-eval/eval-expr* {:env empty-env :senv senv} v))))
             empty-env
             (halite-envs/bindings env))]
    (when (seq unbound-symbols)
      (throw-err (h-err/symbols-not-bound {:unbound-symbols unbound-symbols, :tenv tenv, :env env})))
    (doseq [sym declared-symbols]
      (let [declared-type (get (halite-envs/scope tenv) sym)
            value (optionally-with-eval-bindings type-check?
                                                 (halite-eval/eval-expr* {:env empty-env :senv senv} (get (halite-envs/bindings env) sym)))

            actual-type (halite-type-check/type-of senv tenv value)]
        (when-not (halite-types/subtype? actual-type declared-type)
          (throw-err (h-err/value-of-wrong-type {:variable sym :value value :expected declared-type :actual actual-type})))))
    (optionally-with-eval-bindings type-check?
                                   (halite-eval/eval-expr* {:env env :senv senv} expr))))

(s/defn eval-expr :- s/Any
  "Type check a halite expression against the given type environment,
  evaluate it in the given environment, and return the result. The bindings
  in the environment are checked against the type environment before evaluation."
  [senv :- (s/protocol halite-envs/SpecEnv), tenv :- (s/protocol halite-envs/TypeEnv), env :- (s/protocol halite-envs/Env), expr]
  (eval-expr-general true senv tenv env expr))

(s/defn eval-only-expr :- s/Any
  "Evaluate a halite expression against the given type environment, and return the result. The
  bindings in the environment are checked against the type environment before evaluation."
  [senv :- (s/protocol halite-envs/SpecEnv), tenv :- (s/protocol halite-envs/TypeEnv), env :- (s/protocol halite-envs/Env), expr]
  (eval-expr-general false senv tenv env expr))

;;

(potemkin/import-vars
 [halite-type-check
  type-check type-check-spec type-check-refinement-expr])

(potemkin/import-vars
 [halite-syntax-check
  syntax-check check-n])

(potemkin/import-vars
 [halite-base
  integer-or-long? fixed-decimal? check-count])

(potemkin/import-vars
 [halite-eval
  h< h> h<= h>= h+ h-])
