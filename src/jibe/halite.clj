;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite
  "Expression language for resource spec constraints and refinements that is almost, but
  not quite, a drop-in replacement for salt."
  (:require [clojure.math.numeric-tower :refer [expt]]
            [clojure.set :as set]
            [clojure.string :as str]
            [jibe.halite.types :refer :all]
            [jibe.halite.envs :as halite-envs :refer :all]
            [schema.core :as s])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def reserved-words #{'no-value-})

(declare eval-expr)

(declare type-check*)

(s/defschema ^:private TypeContext {:senv (s/protocol SpecEnv) :tenv (s/protocol TypeEnv)})

(s/defschema ^:private EvalContext {:senv (s/protocol SpecEnv) :env (s/protocol Env)})

(def ^:private ^:dynamic *refinements*)

(defn- long? [value]
  (instance? Long value))

(s/defn ^:private eval-predicate :- s/Bool
  [ctx :- EvalContext, tenv :- (s/protocol TypeEnv), err-msg :- s/Str, bool-expr]
  (try
    (true? (eval-expr (:senv ctx) tenv (:env ctx) bool-expr))
    (catch ExceptionInfo ex
      (throw (ex-info err-msg {:form bool-expr} ex)))))

(s/defn ^:private eval-refinement :- (s/maybe s/Any)
  "Returns an instance of type spec-id, projected from the instance vars in ctx,
  or nil if the guards prevent this projection."
  [ctx :- EvalContext, tenv :- (s/protocol TypeEnv),  spec-id :- NamespacedKeyword, expr]
  (if (contains? *refinements* spec-id)
    (*refinements* spec-id) ;; cache hit
    (eval-expr (:senv ctx) tenv (:env ctx) expr)))

(s/defn ^:private refines-to? :- s/Bool
  [inst spec-id]
  (or (= spec-id (:$type inst))
      (boolean (get (:refinements (meta inst)) spec-id))))

(s/defn ^:private concrete? :- s/Bool
  "Returns true if v is fully concrete (i.e. does not contain a value of an abstract specification), false otherwise."
  [senv :- (s/protocol SpecEnv), v]
  (cond
    (or (long? v) (boolean? v) (string? v)) true
    (map? v) (let [spec-id (:$type v)
                   spec-info (or (lookup-spec senv spec-id)
                                 (throw (ex-info (str "resource spec not found: " spec-id) {:spec-id spec-id})))]
               (and (not (:abstract? spec-info))
                    (every? (partial concrete? senv) (vals (dissoc v :$type)))))
    (coll? v) (every? (partial concrete? senv) v)
    :else (throw (ex-info (format "BUG! Not a value: %s" (pr-str v)) {:value v}))))

(s/defn ^:private check-against-declared-type
  "Runtime check that v conforms to the given type, which is the type of v as declared in a resource spec.
  This function supports the semantics of abstract specs. A declared type of :foo/Bar is replaced by :Instance
  during type checking when the spec foo/Bar is abstract. The type system ensures that v is some instance,
  but at runtime, we need to confirm that v actually refines to the expected type. This function does,
  and recursively deals with collection types."
  [declared-type :- HaliteType, v]
  (cond
    (spec-type? declared-type) (when-not (refines-to? v declared-type)
                                 (throw (ex-info (format "No active refinement path from '%s' to '%s'" (symbol (:$type v)) (symbol declared-type))
                                                 {:value v})))
    (vector? declared-type) (if (= :Maybe (first declared-type))
                              (check-against-declared-type (second declared-type) v)
                              (dorun (map (partial check-against-declared-type (second declared-type)) v)))
    :else nil))

(s/defn ^:private validate-instance :- s/Any
  "Check that an instance satisfies all applicable constraints.
  Return the instance if so, throw an exception if not.
  Assumes that the instance has been type-checked successfully against the given type environment."
  [senv :- (s/protocol SpecEnv), inst :- s/Any]
  (let [spec-id (:$type inst)
        {:keys [spec-vars refines-to] :as spec-info} (lookup-spec senv spec-id)
        spec-tenv (type-env-from-spec senv spec-info)
        env (env-from-inst spec-info inst)
        ctx {:senv senv, :env env}
        satisfied? (fn [[cname expr]]
                     (eval-predicate ctx spec-tenv (format "invalid constraint '%s' of spec '%s'" cname (symbol spec-id)) expr))]

    ;; check that all variables have values that are concrete and that conform to the
    ;; types declared in the parent resource spec
    (doseq [[kw v] (dissoc inst :$type)
            :let [declared-type (spec-vars kw)]]
      ;; TODO: consider letting instances of abstract spec contain abstract values
      (when-not (concrete? senv v)
        (throw (ex-info "instance cannot contain abstract value" {:value v})))
      (check-against-declared-type declared-type v))

    ;; check all constraints
    (let [violated-constraints (->> spec-info :constraints (remove satisfied?))]
      (when (seq violated-constraints)
        (throw (ex-info (format "invalid instance of '%s', violates constraints %s"
                                (symbol spec-id) (str/join ", " (map first violated-constraints)))
                        {:value inst
                         :halite-error :constraint-violation}))))

    ;; fully explore all active refinement paths, and store the results
    (with-meta
      inst
      {:refinements
       (->> refines-to
            (sort-by first)
            (reduce
             (fn [transitive-refinements [spec-id {:keys [expr inverted?]}]]
               (binding [*refinements* transitive-refinements]
                 (let [inst (try (eval-refinement ctx spec-tenv spec-id expr)
                                 (catch ExceptionInfo ex
                                   (if (and inverted? (= :constraint-violation (:halite-error (ex-data ex))))
                                     ex
                                     (throw ex))))]
                   (cond-> transitive-refinements
                     (not= :Unset inst) (->
                                         (merge (:refinements (meta inst)))
                                         (assoc spec-id inst))))))
             {}))})))

(s/defn ^:private check-instance :- NamespacedKeyword
  [check-fn :- clojure.lang.IFn, error-key :- s/Keyword, ctx :- TypeContext, inst :- {s/Keyword s/Any}]
  (let [t (or (:$type inst)
              (throw (ex-info "instance literal must have :$type field" {error-key inst})))
        _ (when-not (and (keyword? t) (namespaced? t))
            (throw (ex-info "expected namespaced keyword as value of :$type" {error-key inst})))
        spec-info (or (lookup-spec (:senv ctx) t)
                      (throw (ex-info (str "resource spec not found: " t) {error-key inst})))
        field-types (:spec-vars spec-info)
        fields (set (keys field-types))
        required-fields (->> field-types
                             (remove (comp maybe-type? second))
                             (map first)
                             set)
        supplied-fields (disj (set (keys inst)) :$type)
        missing-fields (set/difference required-fields supplied-fields)
        invalid-fields (set/difference supplied-fields fields)]

    (when (seq missing-fields)
      (throw (ex-info (str "missing required variables: " (str/join "," missing-fields))
                      {error-key inst :missing-vars missing-fields})))
    (when (seq invalid-fields)
      (throw (ex-info (str "variables not defined on spec: " (str/join "," invalid-fields))
                      {error-key inst :invalid-vars invalid-fields})))

    ;; type-check variable values
    (doseq [[field-kw field-val] (dissoc inst :$type)]
      (let [field-type (substitute-instance-type (:senv ctx) (get field-types field-kw))
            actual-type (check-fn ctx field-val)]
        (when-not (subtype? actual-type field-type)
          (throw (ex-info (str "value of " field-kw " has wrong type")
                          {error-key inst :variable field-kw :expected field-type :actual actual-type})))))
    t))

(s/defn ^:private check-coll :- HaliteType
  [check-fn :- clojure.lang.IFn, error-key :- s/Keyword, ctx :- TypeContext, coll]
  (let [elem-types (map (partial check-fn ctx) coll)
        coll-type (cond
                    (vector? coll) :Vec
                    (set? coll) :Set
                    :else (throw (ex-info "Invalid value" {error-key coll})))]
    (doseq [[elem elem-type] (map vector coll elem-types)]
      (when (maybe-type? elem-type)
        (throw (ex-info (format "%s literal element may not always evaluate to a value" ({:Vec "vector" :Set "set"} coll-type))
                        {error-key elem}))))
    [coll-type
     (condp = (count coll)
       0 :Nothing
       1 (first elem-types)
       (reduce meet elem-types))]))

(s/defn ^:private type-of* :- HaliteType
  [ctx :- TypeContext, value]
  (cond
    (boolean? value) :Boolean
    (long? value) :Integer
    (string? value) :String
    (= :Unset value) :Unset
    (map? value) (let [t (check-instance type-of* :value ctx value)]
                   (validate-instance (:senv ctx) value)
                   t)
    (coll? value) (check-coll type-of* :value ctx value)
    :else (throw (ex-info "Invalid value" {:value value}))))

(s/defn type-of :- HaliteType
  "Return the type of the given runtime value, or throw an error if the value is invalid and cannot be typed.
  For instances, this function checks all applicable constraints. Any constraint violations result in a thrown exception."
  [senv :- (s/protocol SpecEnv), tenv :- (s/protocol TypeEnv), value :- s/Any]
  (type-of* {:senv senv :tenv tenv} value))

(s/defschema FnSignature
  {:arg-types [HaliteType]
   (s/optional-key :variadic-tail) HaliteType
   :return-type HaliteType})

(s/defschema Builtin
  {:signatures (s/constrained [FnSignature] seq)
   :impl clojure.lang.IFn})

(def & :&)

(s/defn ^:private mk-builtin :- Builtin
  [impl & signatures]
  (when (not= 0 (mod (count signatures) 2))
    (throw (ex-info "argument count must be a multiple of 2" {})))
  {:impl impl
   :signatures
   (vec (for [[arg-types return-type] (partition 2 signatures)
              :let [n (count arg-types)
                    variadic? (and (< 1 n) (= :& (nth arg-types (- n 2))))]]
          (cond-> {:arg-types (cond-> arg-types variadic? (subvec 0 (- n 2)))
                   :return-type return-type}
            variadic? (assoc :variadic-tail (last arg-types)))))})

(def ^:private builtins
  (s/with-fn-validation
    {'+ (mk-builtin + [:Integer :Integer & :Integer] :Integer)
     '- (mk-builtin - [:Integer :Integer & :Integer] :Integer)
     '* (mk-builtin * [:Integer :Integer & :Integer] :Integer)
     '< (mk-builtin < [:Integer :Integer] :Boolean)
     '<= (mk-builtin <= [:Integer :Integer] :Boolean)
     '> (mk-builtin > [:Integer :Integer] :Boolean)
     '>= (mk-builtin >= [:Integer :Integer] :Boolean)
     'Cardinality (mk-builtin count [[:Coll :Object]] :Integer) ;; deprecated
     'count (mk-builtin count [[:Coll :Object]] :Integer)
     'and (mk-builtin (fn [& args] (every? true? args))
                      [:Boolean & :Boolean] :Boolean)
     'or (mk-builtin (fn [& args] (true? (some true? args)))
                     [:Boolean & :Boolean] :Boolean)
     'not (mk-builtin not [:Boolean] :Boolean)
     '=> (mk-builtin (fn [a b] (if a b true))
                     [:Boolean :Boolean] :Boolean)
     'contains? (mk-builtin contains? [[:Set :Object] :Object] :Boolean)
     'inc (mk-builtin inc [:Integer] :Integer)
     'dec (mk-builtin dec [:Integer] :Integer)
     'div (mk-builtin quot [:Integer :Integer] :Integer)
     'mod* (mk-builtin mod [:Integer :Integer] :Integer) ;; deprecated
     'mod (mk-builtin mod [:Integer :Integer] :Integer)
     'expt (mk-builtin (fn [x p]
                         (when (neg? p)
                           (throw (ex-info "Invalid exponent" {:p p})))
                         (expt x p)) [:Integer :Integer] :Integer)
     'abs (mk-builtin abs [:Integer] :Integer)
     'str (mk-builtin str [& :String] :String)
     'subset? (mk-builtin set/subset? [[:Set :Object] [:Set :Object]] :Boolean)
     'sort (mk-builtin (comp vec sort)
                       [[:Set :Nothing]] [:Vec :Nothing]
                       [[:Vec :Nothing]] [:Vec :Nothing]
                       [[:Set :Integer]] [:Vec :Integer]
                       [[:Vec :Integer]] [:Vec :Integer])
     'some? (mk-builtin (fn [v] (not= :Unset v)) ;; deprecated
                        [:Any] :Boolean)
     'range (mk-builtin (comp vec range)
                        [:Integer :Integer :Integer] [:Vec :Integer]
                        [:Integer :Integer] [:Vec :Integer]
                        [:Integer] [:Vec :Integer])}))

(s/defn syntax-check
  [expr]
  (cond
    (boolean? expr) true
    (long? expr) true
    (string? expr) true
    (symbol? expr) true
    (keyword? expr) true
    (map? expr) (and (or (:$type expr)
                         (throw (ex-info "instance literal must have :$type field" {:expr expr})))
                     (->> expr
                          (mapcat identity)
                          (map syntax-check)
                          dorun))
    (seq? expr) (and (or (#{'concrete?
                            'sort-by
                            'first
                            'rest
                            'if
                            'any?
                            'valid
                            'concat
                            'when
                            'union
                            '=
                            'every?
                            'if-value-
                            'refines-to?
                            'into
                            'valid?
                            'let
                            'map
                            'reduce
                            'not=
                            'get
                            'difference
                            'if-value
                            'refine-to
                            'conj
                            'intersection
                            'get*
                            'filter} (first expr))
                         (get builtins (first expr))
                         (throw (ex-info "unknown function or operator" {:op (first expr)
                                                                         :expr expr})))
                     (->> (rest expr)
                          (map syntax-check)
                          dorun))
    (or (vector? expr)
        (set? expr)) (->> (map syntax-check expr) dorun)
    :else (throw (ex-info "Syntax error" {:form expr}))))

(s/defn ^:private matches-signature?
  [sig :- FnSignature, actual-types :- [HaliteType]]
  (let [{:keys [arg-types variadic-tail]} sig]
    (and
     (<= (count arg-types) (count actual-types))
     (every? true? (map #(subtype? %1 %2) actual-types arg-types))
     (or (and (= (count arg-types) (count actual-types))
              (nil? variadic-tail))
         (every? true? (map #(when variadic-tail (subtype? %1 variadic-tail))
                            (drop (count arg-types) actual-types)))))))

(s/defn ^:private type-check-fn-application :- HaliteType
  [ctx :- TypeContext, form :- [(s/one BareSymbol :op) s/Any]]
  (let [[op & args] form
        nargs (count args)
        {:keys [signatures impl] :as builtin} (get builtins op)
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
        (matches-signature? sig actual-types) (:return-type sig)
        :else (recur more)))))

(s/defn ^:private type-check-symbol :- HaliteType
  [ctx :- TypeContext, sym]
  (or (get (scope (:tenv ctx)) sym)
      (throw (ex-info (str "Undefined: '" (name sym) "'") {:form sym}))))

(defn- arg-count-exactly
  [n form]
  (when (not= n (count (rest form)))
    (throw (ex-info (format "Wrong number of arguments to '%s': expected %d, but got %d"
                            (name (first form)) n (count (rest form)))
                    {:form form}))))

(defn- arg-count-at-least
  [n form]
  (when (< (count (rest form)) n)
    (throw (ex-info (format "Wrong number of arguments to '%s': expected at least %d, but got %d"
                            (name (first form)) n (count (rest form)))
                    {:form form}))))

(s/defn ^:private type-check-get :- HaliteType
  [ctx :- TypeContext, form]
  (arg-count-exactly 2 form)
  (let [[_ subexpr index] form
        subexpr-type (type-check* ctx subexpr)]
    (cond
      (subtype? subexpr-type [:Vec :Object])
      (let [index-type (type-check* ctx index)]
        (when (= [:Vec :Nothing] subexpr-type)
          (throw (ex-info "Cannot index into empty vector" {:form form})))
        (when (not= :Integer index-type)
          (throw (ex-info "Second argument to get must be an integer when first argument is a vector"
                          {:form index :expected :Integer :actual-type index-type})))
        (second subexpr-type))

      (spec-type? subexpr-type)
      (let [field-types (->> subexpr-type (lookup-spec (:senv ctx)) :spec-vars)]
        (when-not (and (keyword? index) (bare? index))
          (throw (ex-info "Second argument to get must be a variable name (as a keyword) when first argument is an instance"
                          {:form form})))
        (when-not (contains? field-types index)
          (throw (ex-info (format "No such variable '%s' on spec '%s'" (name index) subexpr-type)
                          {:form form})))
        (substitute-instance-type (:senv ctx) (get field-types index)))

      :else (throw (ex-info "First argument to get must be an instance of known type or non-empty vector"
                            {:form form, :actual-type subexpr-type})))))

(s/defn ^:private type-check-equals :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-at-least 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (reduce
     (fn [s t]
       (let [j (join s t)]
         (when (= j :Nothing)
           (throw (ex-info (format "Result of '%s' would always be %s"
                                   (first expr)
                                   (if (= '= (first expr)) "false" "true"))
                           {:form expr})))
         j))
     arg-types))
  :Boolean)

(s/defn ^:private type-check-if :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 3 expr)
  (let [[pred-type s t] (mapv (partial type-check* ctx) (rest expr))]
    (when (not= :Boolean pred-type)
      (throw (ex-info "First argument to 'if' must be boolean" {:form expr})))
    (meet s t)))

(s/defn ^:private type-check-when :- HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 2 expr)
  (let [[pred-type body-type] (map (partial type-check* ctx) (rest expr))]
    (when (not= :Boolean pred-type)
      (throw (ex-info "First argument to 'when' must be boolean" {:form expr})))
    (if (and (vector? body-type) (= :Maybe (first body-type)))
      body-type
      [:Maybe body-type])))

(s/defn ^:private type-check-let :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 2 expr)
  (let [[bindings body] (rest expr)]
    (when-not (zero? (mod (count bindings) 2))
      (throw (ex-info "let bindings form must have an even number of forms" {:form expr})))
    (type-check*
     (reduce
      (fn [ctx [sym body]]
        (when-not (symbol? sym)
          (throw (ex-info "even-numbered forms in let binding vector must be symbols" {:form expr})))
        (when (contains? reserved-words sym)
          (throw (ex-info (format "cannot bind value to reserved word '%s'" sym) {:form expr})))
        (update ctx :tenv extend-scope sym (type-check* ctx body)))
      ctx
      (partition 2 bindings))
     body)))

(s/defn ^:private type-check-comprehend
  [ctx :- TypeContext, expr]
  (arg-count-exactly 2 expr)
  (let [[op [sym expr :as bindings] body] expr]
    (when-not (= 2 (count bindings))
      (throw (ex-info (str "Binding form for '" op "' must have one variable and one collection")
                      {:form expr})))
    (when-not (and (symbol? sym) (bare? sym))
      (throw (ex-info (str "Binding target for '" op "' must be a bare symbol, not: " (pr-str sym))
                      {:form expr})))
    (let [coll-type (type-check* ctx expr)
          et (elem-type coll-type)
          _ (when-not et
              (throw (ex-info (str "collection required for '" op "', not " (pr-str coll-type))
                              {:form expr, :expr-type coll-type})))
          body-type (type-check* (update ctx :tenv extend-scope sym et) body)]
      {:coll-type coll-type
       :body-type body-type})))

(s/defn ^:private type-check-quantifier :- HaliteType
  [ctx :- TypeContext, expr]
  (when (not= :Boolean (:body-type (type-check-comprehend ctx expr)))
    (throw (ex-info (str "Body expression in '" (first expr) "' must be boolean")
                    {:form expr})))
  :Boolean)

(s/defn ^:private type-check-map :- HaliteType
  [ctx :- TypeContext, expr]
  (let [{:keys [coll-type body-type]} (type-check-comprehend ctx expr)]
    [(first coll-type) (if (subtype? coll-type [:Coll :Nothing])
                         :Nothing
                         body-type)]))

(s/defn ^:private type-check-filter :- HaliteType
  [ctx :- TypeContext, expr]
  (let [{:keys [coll-type body-type]} (type-check-comprehend ctx expr)]
    (when (not= :Boolean body-type)
      (throw (ex-info "Body expression in 'filter' must be boolean" {:form expr})))
    coll-type))

(s/defn ^:private type-check-sort-by :- HaliteType
  [ctx :- TypeContext, expr]
  (let [{:keys [coll-type body-type]} (type-check-comprehend ctx expr)]
    (when (not= :Integer body-type)
      (throw (ex-info (str "Body expression in 'sort-by' must be Integer, not "
                           (pr-str body-type))
                      {:form expr})))
    [:Vec (second coll-type)]))

(s/defn ^:private type-check-reduce :- HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 3 expr)
  (let [[op [acc init] [elem coll] body] expr]
    (when-not (and (symbol? acc) (bare? acc))
      (throw (ex-info (str "Accumulator binding target for '" op "' must be a bare symbol, not: "
                           (pr-str acc))
                      {:form expr :accumulator acc})))
    (when-not (and (symbol? elem) (bare? elem))
      (throw (ex-info (str "Element binding target for '" op "' must be a bare symbol, not: "
                           (pr-str elem))
                      {:form expr :element elem})))
    (when (= acc elem)
      (throw (ex-info (str "Cannot use the same symbol for accumulator and element binding: "
                           (pr-str elem))
                      {:form expr :accumulator acc :element elem})))
    (let [init-type (type-check* ctx init)
          coll-type (type-check* ctx coll)
          et (elem-type coll-type)]
      (when-not (subtype? coll-type [:Vec :Object])
        (throw (ex-info (str "Second binding expression to 'reduce' must be a vector.")
                        {:form expr, :actual-coll-type coll-type})))
      (type-check* (update ctx :tenv #(-> %
                                          (extend-scope acc init-type)
                                          (extend-scope elem et)))
                   body))))

(s/defn ^:private type-check-if-value :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 3 expr)
  (let [[op sym set-expr unset-expr] expr]
    (when-not (and (symbol? sym) (bare? sym))
      (throw (ex-info (str "First argument to '" op "' must be a bare symbol") {:form expr})))
    (let [sym-type (type-check* ctx sym)
          unset-type (type-check* ctx unset-expr)]
      (when-not (maybe-type? sym-type)
        (throw (ex-info (str "First argument to '" op "' must have an optional type")
                        {:form sym :expected [:Maybe :Any] :actual sym-type})))
      (if (= :Unset sym-type)
        (do
          (type-check* (update ctx :tenv extend-scope sym :Any) set-expr)
          unset-type)
        (let [inner-type (second sym-type)
              set-type (type-check* (update ctx :tenv extend-scope sym inner-type) set-expr)]
          (meet set-type unset-type))))))

(defn- check-all-sets [[op :as expr] arg-types]
  (when-not (every? #(subtype? % [:Set :Object]) arg-types)
    (throw (ex-info (format "Arguments to '%s' must be sets" op) {:form expr}))))

(s/defn ^:private type-check-union :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-at-least 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (check-all-sets expr arg-types)
    (reduce meet [:Set :Nothing] arg-types)))

(s/defn ^:private type-check-intersection :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-at-least 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (check-all-sets expr arg-types)
    (if (empty? arg-types)
      [:Set :Nothing]
      (reduce join arg-types))))

(s/defn ^:private type-check-difference :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (check-all-sets expr arg-types)
    (first arg-types)))

(s/defn ^:private type-check-first :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 1 expr)
  (let [arg-type (type-check* ctx (second expr))]
    (when-not (subtype? arg-type [:Vec :Object])
      (throw (ex-info "Argument to 'first' must be a vector" {:form expr})))
    (when (= [:Vec :Nothing] arg-type)
      (throw (ex-info "argument to first is always empty" {:form expr})))
    (second arg-type)))

(s/defn ^:private type-check-rest :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 1 expr)
  (let [arg-type (type-check* ctx (second expr))]
    (when-not (subtype? arg-type [:Vec :Object])
      (throw (ex-info "Argument to 'rest' must be a vector" {:form expr})))
    arg-type))

(s/defn ^:private type-check-conj :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-at-least 2 expr)
  (let [[base-type & elem-types] (mapv (partial type-check* ctx) (rest expr))]
    (when-not (subtype? base-type [:Coll :Object])
      (throw (ex-info "First argument to 'conj' must be a set or vector" {:form expr})))
    (let [col-type (if (subtype? base-type [:Vec :Object]) :Vec :Set)]
      (doseq [[elem elem-type] (map vector (drop 2 expr) elem-types)]
        (when (maybe-type? elem-type)
          (throw (ex-info (format "cannot conj possibly unset value to %s" ({:Vec "vector" :Set "set"} col-type))
                          {:form elem}))))
      (reduce meet base-type (map #(vector col-type %) elem-types)))))

(s/defn ^:private type-check-concat :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 2 expr)
  (let [op (first expr)
        [s t] (mapv (partial type-check* ctx) (rest expr))]
    (when-not (subtype? s [:Coll :Object])
      (throw (ex-info (format "First argument to '%s' must be a set or vector" op) {:form expr})))
    (when-not (subtype? t [:Coll :Object])
      (throw (ex-info (format "Second argument to '%s' must be a set or vector" op) {:form expr})))
    (when (and (subtype? s [:Vec :Object]) (not (subtype? t [:Vec :Object])))
      (throw (ex-info (format "When first argument to '%s' is a vector, second argument must also be a vector" op)
                      {:form expr})))
    (let [elem-type (second t)
          col-type (if (subtype? s [:Vec :Object]) :Vec :Set)]
      (meet s [col-type elem-type]))))

(s/defn ^:private type-check-refine-to :- HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 2 expr)
  (let [[subexpr kw] (rest expr)
        s (type-check* ctx subexpr)]
    (when-not (subtype? s :Instance)
      (throw (ex-info "First argument to 'refine-to' must be an instance" {:form expr :actual s})))
    (when-not (spec-type? kw)
      (throw (ex-info "Second argument to 'refine-to' must be a spec id" {:form expr})))
    (when-not (lookup-spec (:senv ctx) kw)
      (throw (ex-info (format "Spec not found: '%s'" (symbol kw)) {:form expr})))
    kw))

(s/defn ^:private type-check-refines-to? :- HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 2 expr)
  (let [[subexpr kw] (rest expr)
        s (type-check* ctx subexpr)]
    (when-not (subtype? s :Instance)
      (throw (ex-info "First argument to 'refines-to?' must be an instance" {:form expr})))
    (when-not (spec-type? kw)
      (throw (ex-info "Second argument to 'refines-to?' must be a spec id" {:form expr})))
    (when-not (lookup-spec (:senv ctx) kw)
      (throw (ex-info (format "Spec not found: '%s'" (symbol kw)) {:form expr})))
    :Boolean))

(s/defn ^:private type-check-concrete? :- HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 1 expr)
  :Boolean)

(s/defn ^:private type-check-valid :- HaliteType
  [ctx :- TypeContext, [_valid subexpr :as expr]]
  (let [t (type-check* ctx subexpr)]
    (cond
      (spec-type? t) [:Maybe t]
      ;; questionable...
      ;;(and (vector? t) (= :Maybe (first t)) (spec-type? (second t))) t
      :else (throw (ex-info "Argument to 'valid' must be an instance of known type" {:form expr})))))

(s/defn ^:private type-check-valid? :- HaliteType
  [ctx :- TypeContext, [_valid? subexpr :as expr]]
  (let [t (type-check* ctx subexpr)]
    (cond
      (spec-type? t) :Boolean
      ;; questionable...
      ;;(and (vector? t) (= :Maybe (first t)) (spec-type? (second t))) :Boolean
      :else (throw (ex-info "Argument to 'valid?' must be an instance of known type" {:form expr})))))

(s/defn ^:private type-check* :- HaliteType
  [ctx :- TypeContext, expr]
  (cond
    (boolean? expr) :Boolean
    (long? expr) :Integer
    (string? expr) :String
    (symbol? expr) (if (= 'no-value- expr)
                     :Unset
                     (type-check-symbol ctx expr))
    (map? expr) (check-instance type-check* :form ctx expr)
    (seq? expr) (condp = (first expr)
                  'get (type-check-get ctx expr)
                  'get* (type-check-get ctx expr) ;; deprecated
                  '= (type-check-equals ctx expr)
                  'not= (type-check-equals ctx expr) ; = and not= have same typing rule
                  'if (type-check-if ctx expr)
                  'when (type-check-when ctx expr)
                  'let (type-check-let ctx expr)
                  'if-value (type-check-if-value ctx expr)
                  'if-value- (type-check-if-value ctx expr) ;; deprecated
                  'union (type-check-union ctx expr)
                  'intersection (type-check-intersection ctx expr)
                  'difference (type-check-difference ctx expr)
                  'first (type-check-first ctx expr)
                  'rest (type-check-rest ctx expr)
                  'conj (type-check-conj ctx expr)
                  'into (type-check-concat ctx expr) ;; deprecated
                  'concat (type-check-concat ctx expr)
                  'refine-to (type-check-refine-to ctx expr)
                  'refines-to? (type-check-refines-to? ctx expr)
                  'concrete? (type-check-concrete? ctx expr)
                  'every? (type-check-quantifier ctx expr)
                  'any? (type-check-quantifier ctx expr)
                  'map (type-check-map ctx expr)
                  'filter (type-check-filter ctx expr)
                  'valid (type-check-valid ctx expr)
                  'valid? (type-check-valid? ctx expr)
                  'sort-by (type-check-sort-by ctx expr)
                  'reduce (type-check-reduce ctx expr)
                  (type-check-fn-application ctx expr))
    (coll? expr) (check-coll type-check* :form ctx expr)
    :else (throw (ex-info "Syntax error" {:form expr}))))

(s/defn type-check :- HaliteType
  "Return the type of the expression, or throw an error if the form is syntactically invalid,
  or not well typed in the given typ environment."
  [senv :- (s/protocol SpecEnv) tenv :- (s/protocol TypeEnv) expr :- s/Any]
  (type-check* {:senv senv :tenv tenv} expr))

(declare eval-expr*)

(s/defn ^:private eval-get :- s/Any
  [ctx :- EvalContext, target-expr index]
  (let [target (eval-expr* ctx target-expr)]
    (if (vector? target)
      (nth target (eval-expr* ctx index))
      (get target index :Unset))))

(s/defn ^:private eval-get* :- s/Any ;; deprecated
  [ctx :- EvalContext, target-expr index]
  (let [target (eval-expr* ctx target-expr)]
    (if (vector? target)
      (nth target (dec (eval-expr* ctx index))) ;; 1-based index
      (get target index :Unset))))

(s/defn ^:private eval-let :- s/Any
  [ctx :- EvalContext, bindings body]
  (eval-expr*
   (reduce
    (fn [ctx [sym body]]
      (update ctx :env bind sym (eval-expr* ctx body)))
    ctx
    (partition 2 bindings))
   body))

(s/defn ^:private eval-if-value :- s/Any
  [ctx :- EvalContext, expr]
  (let [[sym then else] (rest expr)]
    (if (not= :Unset (eval-expr* ctx sym))
      (eval-expr* ctx then)
      (eval-expr* ctx else))))

(s/defn ^:private eval-quantifier-bools :- [s/Bool]
  [ctx :- EvalContext,
   [[sym coll] pred]]
  (mapv #(eval-expr* (update ctx :env bind sym %) pred)
        (eval-expr* ctx coll)))

(s/defn ^:private eval-comprehend :- [s/Any]
  [ctx :- EvalContext,
   [[sym coll] expr]]
  (let [coll-val (eval-expr* ctx coll)]
    [coll-val
     (map #(eval-expr* (update ctx :env bind sym %) expr) coll-val)]))

(s/defn ^:private eval-reduce :- s/Any
  [ctx :- EvalContext,
   [op [acc init] [elem coll] body]]
  (reduce (fn [a b]
            (eval-expr* (update ctx :env
                                #(-> % (bind acc a) (bind elem b)))
                        body))
          (eval-expr* ctx init)
          (eval-expr* ctx coll)))

(s/defn ^:private eval-refine-to :- s/Any
  [ctx :- EvalContext, expr]
  (let [[subexp t] (rest expr)
        inst (eval-expr* ctx subexp)
        result (cond-> inst
                 (not= t (:$type inst)) (-> meta :refinements t))]
    (cond
      (instance? Exception result) (throw (ex-info (format "Refinement from '%s' failed unexpectedly: %s"
                                                           (symbol (:$type inst)) (.getMessage ^Exception result))
                                                   {:form expr}
                                                   result))
      (nil? result) (throw (ex-info (format "No active refinement path from '%s' to '%s'"
                                            (symbol (:$type inst)) (symbol t)) {:form expr}))
      :else result)))

(s/defn ^:private eval-expr* :- s/Any
  [ctx :- EvalContext, expr]
  (let [eval-in-env (partial eval-expr* ctx)]
    (cond
      (or (boolean? expr)
          (long? expr)
          (string? expr)) expr
      (symbol? expr) (if (= 'no-value- expr)
                       :Unset
                       (get (bindings (:env ctx)) expr))
      (map? expr) (->> (dissoc expr :$type)
                       (map (fn [[k v]] [k (eval-in-env v)]))
                       (remove (fn [[k v]] (= :Unset v)))
                       (into (select-keys expr [:$type]))
                       (validate-instance (:senv ctx)))
      (seq? expr) (condp = (first expr)
                    'get (apply eval-get ctx (rest expr))
                    'get* (apply eval-get* ctx (rest expr)) ;; deprecated
                    '= (apply = (mapv eval-in-env (rest expr)))
                    'not= (apply not= (mapv eval-in-env (rest expr)))
                    'if (let [[pred then else] (rest expr)]
                          (eval-in-env (if (eval-in-env pred) then else)))
                    'when (let [[pred body] (rest expr)]
                            (if (eval-in-env pred)
                              (eval-in-env body)
                              :Unset))
                    'let (apply eval-let ctx (rest expr))
                    'if-value (eval-if-value ctx expr)
                    'if-value- (eval-if-value ctx expr) ;; deprecated
                    'union (reduce set/union (map eval-in-env (rest expr)))
                    'intersection (reduce set/intersection (map eval-in-env (rest expr)))
                    'difference (apply set/difference (map eval-in-env (rest expr)))
                    'first (or (first (eval-in-env (second expr)))
                               (throw (ex-info "empty vector has no first element" {:form expr})))
                    'rest (let [arg (eval-in-env (second expr))]
                            (if (empty? arg) [] (subvec arg 1)))
                    'conj (apply conj (map eval-in-env (rest expr)))
                    'concat (apply into (map eval-in-env (rest expr)))
                    'into (apply into (map eval-in-env (rest expr))) ;; deprecated
                    'refine-to (eval-refine-to ctx expr)
                    'refines-to? (let [[subexpr kw] (rest expr)
                                       inst (eval-in-env subexpr)]
                                   (refines-to? inst kw))
                    'concrete? (concrete? (:senv ctx) (eval-in-env (second expr)))
                    'every? (every? identity (eval-quantifier-bools ctx (rest expr)))
                    'any? (boolean (some identity (eval-quantifier-bools ctx (rest expr))))
                    'map (let [[coll result] (eval-comprehend ctx (rest expr))]
                           (into (empty coll) result))
                    'filter (let [[coll bools] (eval-comprehend ctx (rest expr))]
                              (into (empty coll) (filter some? (map #(when %1 %2) bools coll))))
                    'valid (try
                             (eval-in-env (second expr))
                             (catch ExceptionInfo ex
                               (if (= :constraint-violation (:halite-error (ex-data ex)))
                                 :Unset
                                 (throw ex))))
                    'valid? (not= :Unset (eval-in-env (list 'valid (second expr))))
                    'sort-by (let [[coll indexes] (eval-comprehend ctx (rest expr))]
                               (mapv #(nth % 1) (sort (map vector indexes coll))))
                    'reduce (eval-reduce ctx expr)
                    (apply (or (:impl (get builtins (first expr)))
                               (throw (ex-info (str "Undefined operator: " (pr-str (first expr)))
                                               {:form expr})))
                           (mapv eval-in-env (rest expr))))
      (vector? expr) (mapv eval-in-env expr)
      (set? expr) (set (map eval-in-env expr))
      (= :Unset expr) :Unset
      :else (throw (ex-info "Invalid expression" {:form expr})))))

(s/defn eval-expr :- s/Any
  "Type check a halite expression against the given type environment,
  evaluate it in the given environment, and return the result. The bindings
  in the environment are checked against the type environment before evaluation."
  [senv :- (s/protocol SpecEnv), tenv :- (s/protocol TypeEnv), env :- (s/protocol Env), expr]
  (type-check senv tenv expr)
  (let [declared-symbols (set (keys (scope tenv)))
        bound-symbols (set (keys (bindings env)))
        unbound-symbols (set/difference declared-symbols bound-symbols)
        empty-env (halite-envs/env {})
        ;; All runtime values are homoiconic. We eval them in an empty environment
        ;; to initialize refinements for all instances.
        env (reduce
             (fn [env [k v]]
               (bind env k (eval-expr* {:env empty-env :senv senv} v)))
             empty-env
             (bindings env))]
    (when (seq unbound-symbols)
      (throw (ex-info (str "symbols in type environment are not bound: " (str/join " ", unbound-symbols)) {:tenv tenv :env env})))
    (doseq [sym declared-symbols]
      (let [declared-type (get (scope tenv) sym)
            value (eval-expr* {:env empty-env :senv senv} (get (bindings env) sym))
            actual-type (type-of senv tenv value)]
        (when-not (subtype? actual-type declared-type)
          (throw (ex-info (format "Supplied value of '%s' has wrong type" (name sym))
                          {:value value :expected declared-type :actual actual-type})))))
    (eval-expr* {:env env :senv senv} expr)))
