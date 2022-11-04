;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.lint
  "Analyize halite expressions to find patterns of usage unnecessary and
  undesireable for users, though legal and supported by the language."
  (:require [clojure.string :as string]
            [clojure.set :as set]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.eval :as eval]
            [com.viasat.halite.h-err :as h-err]
            [com.viasat.halite.l-err :as l-err]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.lib.format-errors :as format-errors :refer [throw-err with-exception-data text]]
            [com.viasat.halite.type-check :as type-check]
            [com.viasat.halite.types :as types]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(declare type-check*)

(s/defschema ^:private TypeContext {:senv (s/protocol envs/SpecEnv) :tenv (s/protocol envs/TypeEnv)})

(def lint-builtins-signatures {'and (base/make-signatures [[:Boolean :Boolean :& :Boolean] :Boolean])
                               'or (base/make-signatures [[:Boolean :Boolean :& :Boolean] :Boolean])
                               'str (base/make-signatures [[:String :String :& :String] :String])})

(s/defn ^:private type-check-fn-application :- types/HaliteType
  [ctx :- TypeContext
   form :- [(s/one types/BareSymbol :op) s/Any]]
  (let [[op & args] form
        nargs (count args)
        {:keys [signatures impl deprecated?] :as builtin} (get eval/builtins op)
        actual-types (map (partial type-check* ctx) args)]
    (doseq [[arg t] (map vector args actual-types)]
      (when (types/nothing-like? t)
        (throw-err (l-err/disallowed-nothing {:form form
                                              :nothing-arg arg}))))
    ;; linter has tighter signature requirements on some builtin functions
    (when-let [lint-signatures (get lint-builtins-signatures op)]
      (when-not (some #(type-check/matches-signature? % actual-types) lint-signatures)
        (throw-err (h-err/no-matching-signature {:form form
                                                 :op (name op)
                                                 :actual-types actual-types
                                                 :signatures lint-signatures}))))
    (loop [[sig & more] signatures]
      (cond
        (type-check/matches-signature? sig actual-types) (:return-type sig)
        :else (recur more)))))

(s/defn ^:private type-check-symbol :- types/HaliteType
  [ctx :- TypeContext
   sym]
  (if (= '$no-value sym)
    :Unset
    (let [t (get (envs/scope (:tenv ctx)) sym)]
      (when (and (= :Unset t)
                 (not (or (= 'no-value sym)
                          (= '$no-value sym))))
        (throw-err (l-err/disallowed-unset-variable {:form sym})))
      t)))

(defn ^:private type-check-lookup [ctx form subexpr-type index]
  (cond
    (types/halite-vector-type? subexpr-type)
    (let [index-type (type-check* ctx index)]
      (when (types/empty-vectors subexpr-type)
        (throw-err (h-err/index-out-of-bounds {:form form :index index :length 0})))
      (types/elem-type subexpr-type))

    (and (types/spec-type? subexpr-type)
         (types/spec-id subexpr-type)
         (not (types/needs-refinement? subexpr-type)))
    (let [field-types (-> (->> subexpr-type types/spec-id (envs/lookup-spec (:senv ctx)) :spec-vars)
                          (update-vals (partial envs/halite-type-from-var-type-if-needed (:senv ctx))))]
      (get field-types index))))

(s/defn ^:private type-check-get :- types/HaliteType
  [ctx :- TypeContext
   form]
  (let [[_ subexpr index] form]
    (type-check-lookup ctx form (type-check* ctx subexpr) index)))

(s/defn ^:private type-check-get-in :- types/HaliteType
  [ctx :- TypeContext
   form]
  (let [[_ subexpr indexes] form]
    (when (empty? indexes)
      (throw-err (l-err/get-in-path-empty {:form form})))
    (reduce (partial type-check-lookup ctx form) (type-check* ctx subexpr) indexes)))

(s/defn ^:private type-check-equals :- types/HaliteType
  [ctx :- TypeContext
   expr :- s/Any]
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (reduce
     (fn [s t]
       (let [j (types/join s t)]
         (when (types/nothing-like? j)
           (throw-err (l-err/result-always-known {:op (first expr)
                                                  :value (if (= '= (first expr)) 'false 'true)
                                                  :form expr})))
         j))
     arg-types))
  :Boolean)

(s/defn ^:private type-check-set-scale :- types/HaliteType
  [ctx :- TypeContext
   expr :- s/Any]
  (let [[_ _ scale] expr
        arg-types (mapv (partial type-check* ctx) (rest expr))]
    (if (zero? scale)
      :Integer
      (types/decimal-type scale))))

(s/defn ^:private type-check-if :- types/HaliteType
  [ctx :- TypeContext
   expr :- s/Any]
  (let [[pred-type s t] (mapv (partial type-check* ctx) (rest expr))]
    (types/meet s t)))

(s/defn ^:private type-check-when :- types/HaliteType
  [ctx :- TypeContext
   expr]
  (let [[pred-type body-type] (map (partial type-check* ctx) (rest expr))]
    (types/maybe-type body-type)))

(s/defn ^:private type-check-let :- types/HaliteType
  [ctx :- TypeContext
   expr :- s/Any]
  (let [[bindings body] (rest expr)]
    (when (zero? (count bindings))
      (throw-err (l-err/let-bindings-empty {:form expr})))
    (type-check*
     (reduce
      (fn [ctx [sym body]]
        (when (re-find #"^[$]" (name sym))
          (throw-err (l-err/binding-target-invalid-symbol {:form expr :sym sym :op 'let})))
        (let [t (type-check* ctx body)]
          (when (= t :Unset)
            (throw-err (l-err/cannot-bind-unset {:form expr :sym sym :body body})))
          (when (types/nothing-like? t)
            (throw-err (l-err/cannot-bind-nothing {:form expr :sym sym :body body})))
          (update ctx :tenv envs/extend-scope sym t)))
      ctx
      (partition 2 bindings))
     body)))

(s/defn ^:private type-check-comprehend
  [ctx :- TypeContext
   expr]
  (let [[op [sym expr :as bindings] body] expr]
    (when (re-find #"^[$]" (name sym))
      (throw-err (l-err/binding-target-invalid-symbol {:op op :form expr :sym sym})))
    (let [coll-type (type-check* ctx expr)
          et (types/elem-type coll-type)
          body-type (type-check* (update ctx :tenv envs/extend-scope sym et) body)]
      {:coll-type coll-type
       :body-type body-type})))

(s/defn ^:private type-check-quantifier :- types/HaliteType
  [ctx :- TypeContext
   expr]
  (type-check-comprehend ctx expr)
  :Boolean)

(s/defn ^:private type-check-map :- types/HaliteType
  [ctx :- TypeContext
   expr]
  (let [{:keys [coll-type body-type]} (type-check-comprehend ctx expr)]
    [(first coll-type) (if (some #(types/subtype? coll-type %) types/empty-colls)
                         :Nothing
                         body-type)]))

(s/defn ^:private type-check-filter :- types/HaliteType
  [ctx :- TypeContext
   expr]
  (let [{:keys [coll-type]} (type-check-comprehend ctx expr)]
    coll-type))

(s/defn ^:private type-check-sort-by :- types/HaliteType
  [ctx :- TypeContext
   expr]
  (let [{:keys [coll-type]} (type-check-comprehend ctx expr)]
    (types/vector-type (types/elem-type coll-type))))

(s/defn ^:private type-check-reduce :- types/HaliteType
  [ctx :- TypeContext
   expr]
  (let [[op [acc init] [elem coll] body] expr]
    (let [init-type (type-check* ctx init)
          coll-type (type-check* ctx coll)
          et (types/elem-type coll-type)]
      (type-check* (update ctx :tenv #(-> %
                                          (envs/extend-scope acc init-type)
                                          (envs/extend-scope elem et)))
                   body))))

(s/defn ^:private type-check-if-value :- types/HaliteType
  "handles if-value and when-value"
  [ctx :- TypeContext
   expr :- s/Any]
  (let [[op sym set-expr unset-expr] expr]
    (let [sym-type (type-check* ctx sym)
          unset-type (if (= 'when-value op)
                       :Unset
                       (type-check* (update ctx :tenv envs/extend-scope sym :Unset) unset-expr))]
      (when-not (types/strict-maybe-type? sym-type)
        (throw-err (l-err/first-argument-not-optional {:op op :form sym :expected (types/maybe-type :Any) :actual sym-type})))
      (let [inner-type (types/no-maybe sym-type)
            set-type (type-check* (update ctx :tenv envs/extend-scope sym inner-type) set-expr)]
        (types/meet set-type unset-type)))))

(s/defn ^:private type-check-if-value-let :- types/HaliteType
  [ctx :- TypeContext
   expr :- s/Any]
  (let [[op [sym maybe-expr] then-expr else-expr] expr]
    (let [maybe-type (type-check* ctx maybe-expr)
          else-type (if (= 'when-value-let op)
                      :Unset
                      (type-check* (update ctx :tenv envs/extend-scope sym :Unset) else-expr))]
      (when-not (types/strict-maybe-type? maybe-type)
        (throw-err (l-err/binding-expression-not-optional {:op op :form maybe-expr :expected (types/maybe-type :Any) :actual maybe-type})))
      (let [inner-type (types/no-maybe maybe-type)
            then-type (type-check* (update ctx :tenv envs/extend-scope sym inner-type) then-expr)]
        (types/meet then-type else-type)))))

(s/defn ^:private type-check-union :- types/HaliteType
  [ctx :- TypeContext
   expr :- s/Any]
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (type-check/check-all-sets expr arg-types)
    (reduce types/meet types/empty-set arg-types)))

(s/defn ^:private type-check-intersection :- types/HaliteType
  [ctx :- TypeContext
   expr :- s/Any]
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (type-check/check-all-sets expr arg-types)
    (if (empty? arg-types)
      types/empty-set
      (reduce types/join arg-types))))

(s/defn ^:private type-check-difference :- types/HaliteType
  [ctx :- TypeContext
   expr :- s/Any]
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (type-check/check-all-sets expr arg-types)
    (first arg-types)))

(s/defn ^:private type-check-first :- types/HaliteType
  [ctx :- TypeContext
   expr :- s/Any]
  (second (type-check* ctx (second expr))))

(s/defn ^:private type-check-rest :- types/HaliteType
  [ctx :- TypeContext
   expr :- s/Any]
  (type-check* ctx (second expr)))

(s/defn ^:private type-check-conj :- types/HaliteType
  [ctx :- TypeContext
   expr :- s/Any]
  (let [[base-type & elem-types] (mapv (partial type-check* ctx) (rest expr))]
    (types/change-elem-type
     base-type
     (reduce types/meet (types/elem-type base-type) elem-types))))

(s/defn ^:private type-check-concat :- types/HaliteType
  [ctx :- TypeContext
   expr :- s/Any]
  (let [op (first expr)
        [s t] (mapv (partial type-check* ctx) (rest expr))]
    (types/meet s
                (types/change-elem-type s (types/elem-type t)))))

(s/defn ^:private type-check-refine-to :- types/HaliteType
  [ctx :- TypeContext
   expr]
  (let [[subexpr kw] (rest expr)
        s (type-check* ctx subexpr)]
    (types/concrete-spec-type kw)))

(s/defn ^:private type-check-refines-to? :- types/HaliteType
  [ctx :- TypeContext
   expr]
  (let [[subexpr kw] (rest expr)
        s (type-check* ctx subexpr)]
    :Boolean))

(s/defn ^:private type-check-valid :- types/HaliteType
  [ctx :- TypeContext
   [_valid subexpr :as expr]]
  (let [t (type-check* ctx subexpr)]
    (types/spec-type? t) (types/maybe-type t)))

(s/defn ^:private type-check-valid? :- types/HaliteType
  [ctx :- TypeContext
   [_valid? subexpr :as expr]]
  (let [t (type-check* ctx subexpr)]
    (types/spec-type? t) :Boolean))

(s/defn ^:private type-check* :- types/HaliteType
  [ctx :- TypeContext
   expr]
  (cond
    (boolean? expr) :Boolean
    (base/integer-or-long? expr) :Integer
    (base/fixed-decimal? expr) (type-check/type-check-fixed-decimal expr)
    (string? expr) :String
    (symbol? expr) (type-check-symbol ctx expr)
    (map? expr) (type-check/type-check-instance type-check* :form ctx expr)
    (seq? expr) (condp = (first expr)
                  'get (type-check-get ctx expr)
                  'get-in (type-check-get-in ctx expr)
                  '= (type-check-equals ctx expr)
                  'not= (type-check-equals ctx expr) ; = and not= have same typing rule
                  'rescale (type-check-set-scale ctx expr)
                  'if (type-check-if ctx expr)
                  'when (type-check-when ctx expr)
                  'let (type-check-let ctx expr)
                  'if-value (type-check-if-value ctx expr)
                  'when-value (type-check-if-value ctx expr) ; if-value type-checks when-value
                  'if-value-let (type-check-if-value-let ctx expr)
                  'when-value-let (type-check-if-value-let ctx expr) ; if-value-let type-checks when-value-let
                  'union (type-check-union ctx expr)
                  'intersection (type-check-intersection ctx expr)
                  'difference (type-check-difference ctx expr)
                  'first (type-check-first ctx expr)
                  'rest (type-check-rest ctx expr)
                  'conj (type-check-conj ctx expr)
                  'concat (type-check-concat ctx expr)
                  'refine-to (type-check-refine-to ctx expr)
                  'refines-to? (type-check-refines-to? ctx expr)
                  'every? (type-check-quantifier ctx expr)
                  'any? (type-check-quantifier ctx expr)
                  'map (type-check-map ctx expr)
                  'filter (type-check-filter ctx expr)
                  'valid (type-check-valid ctx expr)
                  'valid? (type-check-valid? ctx expr)
                  'sort-by (type-check-sort-by ctx expr)
                  'reduce (type-check-reduce ctx expr)
                  (type-check-fn-application ctx expr))
    (coll? expr) (type-check/type-check-coll type-check* :form ctx expr)))

(s/defn lint!
  "Assumes type-checked halite. Return nil if no violations found, or throw the
  first linting rule violation found."
  [senv :- (s/protocol envs/SpecEnv)
   tenv :- (s/protocol envs/TypeEnv)
   expr :- s/Any]
  (some (fn [[sym t]]
          (when (and (re-find #"^[$]" (name sym))
                     (not (contains? base/reserved-words sym))
                     (not (contains? base/external-reserved-words sym)))
            (throw (ex-info (str "Bug: type environment contains disallowed symbol: " sym)
                            {:sym sym :type t}))))
        (envs/scope tenv))
  (type-check* {:senv senv :tenv tenv} expr)
  nil)

(s/defn type-check-and-lint
  "Convenience function to run `lint!` and also `type-check/type-check` in the
  required order.  Returns the halite type of the expr if no lint violations
  found, otherwise throw the first linting rule violation found."
  [senv :- (s/protocol envs/SpecEnv)
   tenv :- (s/protocol envs/TypeEnv)
   expr :- s/Any]
  (let [t (type-check/type-check senv tenv expr)]
    (lint! senv tenv expr)
    t))

(s/defn lint :- types/HaliteType
  "Assumes type-checked halite. Return nil if no violations found, or a seq of
  one or more linting rule violations."
  [senv :- (s/protocol envs/SpecEnv)
   tenv :- (s/protocol envs/TypeEnv)
   expr :- s/Any]
  (try
    (lint! senv tenv expr)
    (catch clojure.lang.ExceptionInfo ex
      [(ex-data ex)])))
