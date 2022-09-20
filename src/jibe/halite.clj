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
            [jibe.lib.format-errors :refer [throw-err]]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

;;

(s/defn type-check :- halite-types/HaliteType
  "Return the type of the expression, or throw an error if the form is syntactically invalid,
  or not well typed in the given typ environment."
  [senv :- (s/protocol halite-envs/SpecEnv) tenv :- (s/protocol halite-envs/TypeEnv) expr :- s/Any]
  (halite-type-check/type-check* {:senv senv :tenv tenv} expr))

(s/defn type-check-spec
  [senv :- (s/protocol halite-envs/SpecEnv), spec-info :- halite-envs/SpecInfo]
  (let [{:keys [constraints refines-to]} spec-info
        tenv (halite-envs/type-env-from-spec senv spec-info)]
    (doseq [[cname cexpr] constraints]
      (when (not= :Boolean (type-check senv tenv cexpr))
        (throw-err (h-err/not-boolean-constraint {:expr cexpr}))))))

(def syntax-check halite-syntax-check/syntax-check)

(def check-n halite-syntax-check/check-n)

(def integer-or-long? halite-base/integer-or-long?)

(def fixed-decimal? halite-base/fixed-decimal?)

(def check-count halite-base/check-count)

(def h< halite-eval/h<)

(def h> halite-eval/h>)

(def h<= halite-eval/h<=)

(def h>= halite-eval/h>=)

(def h+ halite-eval/h+)

(def h- halite-eval/h-)

(s/defn eval-expr :- s/Any
  "Type check a halite expression against the given type environment,
  evaluate it in the given environment, and return the result. The bindings
  in the environment are checked against the type environment before evaluation."
  [senv :- (s/protocol halite-envs/SpecEnv), tenv :- (s/protocol halite-envs/TypeEnv), env :- (s/protocol halite-envs/Env), expr]
  (type-check senv tenv expr)
  (let [declared-symbols (set (keys (halite-envs/scope tenv)))
        bound-symbols (set (keys (halite-envs/bindings env)))
        unbound-symbols (set/difference declared-symbols bound-symbols)
        empty-env (halite-envs/env {})
        ;; All runtime values are homoiconic. We eval them in an empty environment
        ;; to initialize refinements for all instances.
        env (reduce
             (fn [env [k v]]
               (halite-envs/bind env k (halite-type-check/with-eval-bindings
                                         (halite-eval/eval-expr* {:env empty-env :senv senv} v))))
             empty-env
             (halite-envs/bindings env))]
    (when (seq unbound-symbols)
      (throw-err (h-err/symbols-not-bound {:unbound-symbols unbound-symbols, :tenv tenv, :env env})))
    (doseq [sym declared-symbols]
      (let [declared-type (get (halite-envs/scope tenv) sym)
            value (halite-type-check/with-eval-bindings
                    (halite-eval/eval-expr* {:env empty-env :senv senv} (get (halite-envs/bindings env) sym)))
            actual-type (halite-type-check/type-of senv tenv value)]
        (when-not (halite-types/subtype? actual-type declared-type)
          (throw-err (h-err/value-of-wrong-type {:variable sym :value value :expected declared-type :actual actual-type})))))
    (halite-type-check/with-eval-bindings
      (halite-eval/eval-expr* {:env env :senv senv} expr))))
