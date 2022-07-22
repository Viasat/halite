;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.halite-lint
  "Analyize halite expressions to find patterns of usage unnecessary and
  undesireable for users, though legal and supported by the language."
  (:require [jibe.halite :as halite]
            [jibe.halite.halite-types :as halite-types]
            [jibe.halite.halite-envs :as halite-envs]
            [jibe.lib.fixed-decimal :as fixed-decimal]
            [clojure.string :as string]
            [clojure.set :as set]
            [schema.core :as s]))
(declare type-check*)

(s/defschema ^:private TypeContext {:senv (s/protocol halite-envs/SpecEnv) :tenv (s/protocol halite-envs/TypeEnv)})

(s/defn ^:private type-check-fn-application :- halite-types/HaliteType
  [ctx :- TypeContext, form :- [(s/one halite-types/BareSymbol :op) s/Any]]
  (let [[op & args] form
        nargs (count args)
        {:keys [signatures impl deprecated?] :as builtin} (get halite/builtins op)
        actual-types (map (partial type-check* ctx) args)]
    (when (nil? builtin)
      (throw (ex-info (str "function '" op "' not found") {:form form})))
    (doseq [[arg t] (map vector args actual-types)]
      (when (= :Nothing t)
        (throw (ex-info (str "Disallowed ':Nothing' expression: " (pr-str arg))
                        {:form form
                         :nothing-arg arg}))))
    (loop [[sig & more] signatures]
      (cond
        (nil? sig) (throw (ex-info (str "no matching signature for '" (name op) "'")
                                   {:form form
                                    :actual-types actual-types
                                    :signatures signatures}))
        (halite/matches-signature? sig actual-types) (:return-type sig)
        :else (recur more)))))

(s/defn ^:private type-check-symbol :- halite-types/HaliteType
  [ctx :- TypeContext, sym]
  (let [t (get (halite-envs/scope (:tenv ctx)) sym)]
    (when-not t
      (throw (ex-info (str "Undefined: '" (name sym) "'") {:user-visible-error? true
                                                           :form sym})))
    (when (and (= :Unset t)
               (not (or (= 'no-value sym)
                        (= '$no-value sym))))
      (throw (ex-info (str "Disallowed use of Unset variable '" (name sym) "'; you may want '$no-value'")
                      {:form sym})))
    t))

(defn ^:private type-check-lookup [ctx form subexpr-type index]
  (cond
    (halite-types/halite-vector-type? subexpr-type)
    (let [index-type (type-check* ctx index)]
      (when (= halite-types/empty-vector subexpr-type)
        (throw (ex-info "Cannot index into empty vector" {:form form})))
      (when (not= :Integer index-type)
        (throw (ex-info "Index must be an integer when target is a vector"
                        {:form form, :index-form index, :expected :Integer, :actual-type index-type})))
      (halite-types/elem-type subexpr-type))

    (and (halite-types/spec-type? subexpr-type)
         (halite-types/spec-id subexpr-type)
         (not (halite-types/needs-refinement? subexpr-type)))
    (let [field-types (-> (->> subexpr-type halite-types/spec-id (halite-envs/lookup-spec (:senv ctx)) :spec-vars)
                          (update-vals (partial halite-envs/halite-type-from-var-type (:senv ctx))))]
      (when-not (and (keyword? index) (halite-types/bare? index))
        (throw (ex-info "Index must be a variable name (as a keyword) when target is an instance"
                        {:form form, :index-form index})))
      (when-not (contains? field-types index)
        (throw (ex-info (format "No such variable '%s' on spec '%s'" (name index) (halite-types/spec-id subexpr-type))
                        {:form form, :index-form index})))
      (get field-types index))

    :else (throw (ex-info "Lookup target must be an instance of known type or non-empty vector"
                          {:form form, :actual-type subexpr-type}))))

(s/defn ^:private type-check-get :- halite-types/HaliteType
  [ctx :- TypeContext, form]
  (halite/arg-count-exactly 2 form)
  (let [[_ subexpr index] form]
    (type-check-lookup ctx form (type-check* ctx subexpr) index)))

(s/defn ^:private type-check-get-in :- halite-types/HaliteType
  [ctx :- TypeContext, form]
  (halite/arg-count-exactly 2 form)
  (let [[_ subexpr indexes] form]
    (reduce (partial type-check-lookup ctx form) (type-check* ctx subexpr) indexes)))

(s/defn ^:private type-check-equals :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (halite/arg-count-at-least 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (reduce
     (fn [s t]
       (let [j (halite-types/join s t)]
         (when (= j :Nothing)
           (throw (ex-info (format "Result of '%s' would always be %s"
                                   (first expr)
                                   (if (= '= (first expr)) "false" "true"))
                           {:user-visible-error? true
                            :form expr})))
         j))
     arg-types))
  :Boolean)

(s/defn ^:private type-check-set-scale :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (halite/arg-count-exactly 2 expr)
  (let [[_ _ scale] expr
        arg-types (mapv (partial type-check* ctx) (rest expr))]
    (if (zero? scale)
      :Integer
      (halite-types/decimal-type scale))))

(s/defn ^:private type-check-if :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (halite/arg-count-exactly 3 expr)
  (let [[pred-type s t] (mapv (partial type-check* ctx) (rest expr))]
    (when (not= :Boolean pred-type)
      (throw (ex-info "First argument to 'if' must be boolean" {:form expr})))
    (halite-types/meet s t)))

(s/defn ^:private type-check-when :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (halite/arg-count-exactly 2 expr)
  (let [[pred-type body-type] (map (partial type-check* ctx) (rest expr))]
    (when (not= :Boolean pred-type)
      (throw (ex-info "First argument to 'when' must be boolean" {:form expr})))
    (halite-types/maybe-type body-type)))

(s/defn ^:private type-check-let :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (halite/arg-count-exactly 2 expr)
  (let [[bindings body] (rest expr)]
    (type-check*
     (reduce
      (fn [ctx [sym body]]
        (when-not (and (symbol? sym) (halite-types/bare? sym))
          (throw (ex-info (format "Binding target for 'let' must be a bare symbol, not: %s"
                                  (pr-str sym))
                          {:form expr :sym sym})))
        (when (re-find #"^[$]" (name sym))
          (throw (ex-info (format "Binding target for 'let' must not start with '$': " sym)
                          {:form expr :sym sym})))
        (let [t (type-check* ctx body)]
          (when (= t :Unset)
            (throw (ex-info (format "Disallowed binding '%s' to :Unset value; just use '$no-value'" sym)
                            {:form expr :sym sym :body body})))
          (when (= t :Nothing)
            (throw (ex-info (format "Disallowed binding '%s' to :Nothing value; perhaps move to body of 'let'" sym)
                            {:form expr :sym sym :body body})))
          (update ctx :tenv halite-envs/extend-scope sym t)))
      ctx
      (partition 2 bindings))
     body)))

(s/defn ^:private type-check-comprehend
  [ctx :- TypeContext, expr]
  (halite/arg-count-exactly 2 expr)
  (let [[op [sym expr :as bindings] body] expr]
    (when-not (= 2 (count bindings))
      (throw (ex-info (str "Binding form for '" op "' must have one variable and one collection")
                      {:form expr})))
    (when-not (and (symbol? sym) (halite-types/bare? sym))
      (throw (ex-info (str "Binding target for '" op "' must be a bare symbol, not: " (pr-str sym))
                      {:form expr, :sym sym})))
    (when (re-find #"^[$]" (name sym))
      (throw (ex-info (format "Binding target for '%s' must not start with '$': " op sym)
                      {:form expr :sym sym})))
    (let [coll-type (type-check* ctx expr)
          et (halite-types/elem-type coll-type)
          _ (when-not et
              (throw (ex-info (str "collection required for '" op "', not " (pr-str (or (halite-types/spec-id coll-type) coll-type)))
                              {:form expr, :expr-type coll-type})))
          body-type (type-check* (update ctx :tenv halite-envs/extend-scope sym et) body)]
      {:coll-type coll-type
       :body-type body-type})))

(s/defn ^:private type-check-quantifier :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (when (not= :Boolean (:body-type (type-check-comprehend ctx expr)))
    (throw (ex-info (str "Body expression in '" (first expr) "' must be boolean")
                    {:form expr})))
  :Boolean)

(s/defn ^:private type-check-map :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (let [{:keys [coll-type body-type]} (type-check-comprehend ctx expr)]
    [(first coll-type) (if (halite-types/subtype? coll-type halite-types/empty-coll)
                         :Nothing
                         body-type)]))

(s/defn ^:private type-check-filter :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (let [{:keys [coll-type body-type]} (type-check-comprehend ctx expr)]
    (when (not= :Boolean body-type)
      (throw (ex-info "Body expression in 'filter' must be boolean" {:form expr})))
    coll-type))

(s/defn ^:private type-check-sort-by :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (let [{:keys [coll-type body-type]} (type-check-comprehend ctx expr)]
    (when (not= :Integer body-type)
      (throw (ex-info (str "Body expression in 'sort-by' must be Integer, not "
                           (pr-str body-type))
                      {:form expr})))
    (halite-types/vector-type (halite-types/elem-type coll-type))))

(s/defn ^:private type-check-reduce :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (halite/arg-count-exactly 3 expr)
  (let [[op [acc init] [elem coll] body] expr]
    (when-not (and (symbol? acc) (halite-types/bare? acc))
      (throw (ex-info (str "Accumulator binding target for '" op "' must be a bare symbol, not: "
                           (pr-str acc))
                      {:form expr :accumulator acc})))
    (when-not (and (symbol? elem) (halite-types/bare? elem))
      (throw (ex-info (str "Element binding target for '" op "' must be a bare symbol, not: "
                           (pr-str elem))
                      {:form expr :element elem})))
    (when (= acc elem)
      (throw (ex-info (str "Cannot use the same symbol for accumulator and element binding: "
                           (pr-str elem))
                      {:form expr :accumulator acc :element elem})))
    (let [init-type (type-check* ctx init)
          coll-type (type-check* ctx coll)
          et (halite-types/elem-type coll-type)]
      (when-not (halite-types/subtype? coll-type (halite-types/vector-type :Value))
        (throw (ex-info (str "Second binding expression to 'reduce' must be a vector.")
                        {:form expr, :actual-coll-type coll-type})))
      (type-check* (update ctx :tenv #(-> %
                                          (halite-envs/extend-scope acc init-type)
                                          (halite-envs/extend-scope elem et)))
                   body))))

(s/defn ^:private type-check-if-value :- halite-types/HaliteType
  "handles if-value and when-value"
  [ctx :- TypeContext, expr :- s/Any]
  (let [[op sym set-expr unset-expr] expr]
    (halite/arg-count-exactly (if (= 'when-value op) 2 3) expr)
    (when-not (and (symbol? sym) (halite-types/bare? sym))
      (throw (ex-info (str "First argument to '" op "' must be a bare symbol") {:form expr})))
    (let [sym-type (type-check* ctx sym)
          unset-type (if (= 'when-value op)
                       :Unset
                       (type-check* (update ctx :tenv halite-envs/extend-scope sym :Unset) unset-expr))]
      (when-not (halite-types/strict-maybe-type? sym-type)
        (throw (ex-info (str "First argument to '" op "' must have an optional type")
                        {:form sym :expected (halite-types/maybe-type :Any) :actual sym-type})))
      (let [inner-type (halite-types/no-maybe sym-type)
            set-type (type-check* (update ctx :tenv halite-envs/extend-scope sym inner-type) set-expr)]
        (halite-types/meet set-type unset-type)))))

(s/defn ^:private type-check-if-value-let :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (let [[op [sym maybe-expr] then-expr else-expr] expr]
    (halite/arg-count-exactly (if (= 'when-value-let op) 2 3) expr)
    (when-not (and (symbol? sym) (halite-types/bare? sym))
      (throw (ex-info (str "Binding target for '" op "' must be a bare symbol") {:form sym})))
    (let [maybe-type (type-check* ctx maybe-expr)
          else-type (if (= 'when-value-let op)
                      :Unset
                      (type-check* (update ctx :tenv halite-envs/extend-scope sym :Unset) else-expr))]
      (when-not (halite-types/strict-maybe-type? maybe-type)
        (throw (ex-info (str "Binding expression in '" op "' must have an optional type")
                        {:form maybe-expr :expected (halite-types/maybe-type :Any) :actual maybe-type})))
      (let [inner-type (halite-types/no-maybe maybe-type)
            then-type (type-check* (update ctx :tenv halite-envs/extend-scope sym inner-type) then-expr)]
        (halite-types/meet then-type else-type)))))

(s/defn ^:private type-check-union :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (halite/arg-count-at-least 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (halite/check-all-sets expr arg-types)
    (reduce halite-types/meet halite-types/empty-set arg-types)))

(s/defn ^:private type-check-intersection :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (halite/arg-count-at-least 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (halite/check-all-sets expr arg-types)
    (if (empty? arg-types)
      halite-types/empty-set
      (reduce halite-types/join arg-types))))

(s/defn ^:private type-check-difference :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (halite/arg-count-exactly 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (halite/check-all-sets expr arg-types)
    (first arg-types)))

(s/defn ^:private type-check-first :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (halite/arg-count-exactly 1 expr)
  (let [arg-type (type-check* ctx (second expr))]
    (when-not (halite-types/subtype? arg-type (halite-types/vector-type :Value))
      (throw (ex-info "Argument to 'first' must be a vector" {:form expr})))
    (when (= halite-types/empty-vector arg-type)
      (throw (ex-info "argument to first is always empty" {:form expr})))
    (second arg-type)))

(s/defn ^:private type-check-rest :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (halite/arg-count-exactly 1 expr)
  (let [arg-type (type-check* ctx (second expr))]
    (when-not (halite-types/subtype? arg-type (halite-types/vector-type :Value))
      (throw (ex-info "Argument to 'rest' must be a vector" {:form expr})))
    arg-type))

(s/defn ^:private type-check-conj :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (halite/arg-count-at-least 2 expr)
  (let [[base-type & elem-types] (mapv (partial type-check* ctx) (rest expr))]
    (when-not (halite-types/subtype? base-type (halite-types/coll-type :Value))
      (throw (ex-info "First argument to 'conj' must be a set or vector" {:form expr})))
    (doseq [[elem elem-type] (map vector (drop 2 expr) elem-types)]
      (when (halite-types/maybe-type? elem-type)
        (throw (ex-info (format "cannot conj possibly unset value to %s" (halite-types/coll-type-string base-type))
                        {:form elem}))))
    (halite-types/change-elem-type
     base-type
     (reduce halite-types/meet (halite-types/elem-type base-type) elem-types))))

(s/defn ^:private type-check-concat :- halite-types/HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (halite/arg-count-exactly 2 expr)
  (let [op (first expr)
        [s t] (mapv (partial type-check* ctx) (rest expr))]
    (when-not (halite-types/subtype? s (halite-types/coll-type :Value))
      (throw (ex-info (format "First argument to '%s' must be a set or vector" op) {:form expr})))
    (when-not (halite-types/subtype? t (halite-types/coll-type :Value))
      (throw (ex-info (format "Second argument to '%s' must be a set or vector" op) {:form expr})))
    (when (and (halite-types/subtype? s (halite-types/vector-type :Value)) (not (halite-types/subtype? t (halite-types/vector-type :Value))))
      (throw (ex-info (format "When first argument to '%s' is a vector, second argument must also be a vector" op)
                      {:form expr})))
    (halite-types/meet s
                       (halite-types/change-elem-type s (halite-types/elem-type t)))))

(s/defn ^:private type-check-refine-to :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (halite/arg-count-exactly 2 expr)
  (let [[subexpr kw] (rest expr)
        s (type-check* ctx subexpr)]
    (when-not (halite-types/subtype? s (halite-types/instance-type))
      (throw (ex-info "First argument to 'refine-to' must be an instance" {:form expr :actual s})))
    (when-not (halite-types/namespaced-keyword? kw)
      (throw (ex-info "Second argument to 'refine-to' must be a spec id" {:form expr})))
    (when-not (halite-envs/lookup-spec (:senv ctx) kw)
      (throw (ex-info (format "Spec not found: '%s'" (symbol kw)) {:form expr})))
    (halite-types/concrete-spec-type kw)))

(s/defn ^:private type-check-refines-to? :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (halite/arg-count-exactly 2 expr)
  (let [[subexpr kw] (rest expr)
        s (type-check* ctx subexpr)]
    (when-not (halite-types/subtype? s (halite-types/instance-type))
      (throw (ex-info "First argument to 'refines-to?' must be an instance" {:form expr})))
    (when-not (halite-types/namespaced-keyword? kw)
      (throw (ex-info "Second argument to 'refines-to?' must be a spec id" {:form expr})))
    (when-not (halite-envs/lookup-spec (:senv ctx) kw)
      (throw (ex-info (format "Spec not found: '%s'" (symbol kw)) {:form expr})))
    :Boolean))

(s/defn ^:private type-check-valid :- halite-types/HaliteType
  [ctx :- TypeContext, [_valid subexpr :as expr]]
  (let [t (type-check* ctx subexpr)]
    (cond
      (halite-types/spec-type? t) (halite-types/maybe-type t)
      ;; questionable...
      ;;(and (vector? t) (= :Maybe (first t)) (spec-type? (second t))) t
      :else (throw (ex-info "Argument to 'valid' must be an instance of known type" {:form expr})))))

(s/defn ^:private type-check-valid? :- halite-types/HaliteType
  [ctx :- TypeContext, [_valid? subexpr :as expr]]
  (let [t (type-check* ctx subexpr)]
    (cond
      (halite-types/spec-type? t) :Boolean
      ;; questionable...
      ;;(and (vector? t) (= :Maybe (first t)) (spec-type? (second t))) :Boolean
      :else (throw (ex-info "Argument to 'valid?' must be an instance of known type" {:form expr})))))

(s/defn ^:private type-check* :- halite-types/HaliteType
  [ctx :- TypeContext, expr]
  (cond
    (boolean? expr) :Boolean
    (halite/integer-or-long? expr) :Integer
    (halite/fixed-decimal? expr) (halite/type-check-fixed-decimal expr)
    (string? expr) :String
    (symbol? expr) (type-check-symbol ctx expr)
    (map? expr) (halite/check-instance type-check* :form ctx expr)
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
    (coll? expr) (halite/check-coll type-check* :form ctx expr)
    :else (throw (ex-info "Syntax error" {:form expr
                                          :form-class (class expr)}))))

(s/defn lint!
  "Assumes type-checked halite. Return nil if no violations found, or throw the
  first linting rule violation found."
  [senv :- (s/protocol halite-envs/SpecEnv) tenv :- (s/protocol halite-envs/TypeEnv) expr :- s/Any]
  (some (fn [[sym t]]
          (when (and (re-find #"^[$]" (name sym))
                     (not (contains? halite/reserved-words sym))
                     (not (contains? halite/external-reserved-words sym)))
            (throw (ex-info (str "Bug: type environment contains disallowed symbol: " sym)
                            {:sym sym :type t}))))
        (halite-envs/scope tenv))
  (type-check* {:senv senv :tenv tenv} expr)
  nil)

(s/defn type-check
  "Convenience function to run `lint!` and also `halite/type-check` in the
  required order.  Returns the halite type of the expr if no lint violations
  found, otherwise throw the first linting rule violation found."
  [senv :- (s/protocol halite-envs/SpecEnv) tenv :- (s/protocol halite-envs/TypeEnv) expr :- s/Any]
  (let [t (halite/type-check senv tenv expr)]
    (lint! senv tenv expr)
    t))

(s/defn lint :- halite-types/HaliteType
  "Assumes type-checked halite. Return nil if no violations found, or a seq of
  one or more linting rule violations."
  [senv :- (s/protocol halite-envs/SpecEnv) tenv :- (s/protocol halite-envs/TypeEnv) expr :- s/Any]
  (try
    (lint! senv tenv expr)
    (catch clojure.lang.ExceptionInfo ex
      [(ex-data ex)])))
