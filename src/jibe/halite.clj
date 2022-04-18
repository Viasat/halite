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

(s/defn ^:private eval-predicate :- s/Bool
  [ctx :- EvalContext, tenv :- (s/protocol TypeEnv), err-msg :- s/Str, bool-expr]
  (try
    (true? (eval-expr (:senv ctx) tenv (:env ctx) bool-expr))
    (catch ExceptionInfo ex
      (throw (ex-info err-msg {:form bool-expr} ex)))))

(s/defn ^:private eval-refinement :- s/Any
  [ctx :- EvalContext, tenv :- (s/protocol TypeEnv),  spec-id :- NamespacedKeyword, clauses]
  (let [[clause & clauses] clauses
        [clause-name mapping] clause]
    ;; TODO: support for guards
    (or (*refinements* spec-id)
        (eval-expr (:senv ctx) tenv (:env ctx)
                   (assoc mapping :$type spec-id)))))

(s/defn ^:private refines-to? :- s/Bool
  [inst spec-id]
  (or (= spec-id (:$type inst))
      (contains? (:refinements (meta inst)) spec-id)))

(s/defn ^:private concrete? :- s/Bool
  "Returns true if v is fully concrete (i.e. does not contain a value of an abstract specification), false otherwise."
  [senv :- (s/protocol SpecEnv), v]
  (cond
    (or (integer? v) (boolean? v) (string? v)) true
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
             (fn [transitive-refinements [spec-id {:keys [clauses inverted?]}]]
               (binding [*refinements* transitive-refinements]
                 (let [inst (try (eval-refinement ctx spec-tenv spec-id clauses)
                                 (catch ExceptionInfo ex
                                   (if (and inverted? (= :constraint-violation (:halite-error (ex-data ex))))
                                     ex
                                     (throw ex))))]
                   (-> transitive-refinements
                       (merge (:refinements (meta inst)))
                       (assoc spec-id inst)))))
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
            actual-type (check-fn  ctx field-val)]
        (when-not (subtype? actual-type field-type)
          (throw (ex-info (str "value of " field-kw " has wrong type")
                          {error-key inst :variable field-kw :expected field-type :actual actual-type})))))
    t))

(s/defn ^:private check-coll :- HaliteType
  [check-fn :- clojure.lang.IFn, error-key :- s/Keyword, ctx :- TypeContext, coll]
  (cond
    (= [] coll) :EmptyVec
    (= #{} coll) :EmptySet
    :else (let [elem-types (map (partial check-fn ctx) coll)
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
               0 nil
               1 (first elem-types)
               (reduce meet elem-types))])))

(s/defn ^:private type-of* :- HaliteType
  [ctx :- TypeContext, value]
  (cond
    (boolean? value) :Boolean
    (integer? value) :Integer
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
     'Cardinality (mk-builtin count [:Coll] :Integer)
     'count (mk-builtin count [:Coll] :Integer)
     'and (mk-builtin (fn [& args] (every? true? args))
                      [:Boolean & :Boolean] :Boolean)
     'or (mk-builtin (fn [& args] (true? (some true? args)))
                     [:Boolean & :Boolean] :Boolean)
     'not (mk-builtin not [:Boolean] :Boolean)
     '=> (mk-builtin (fn [a b] (if a b true))
                     [:Boolean :Boolean] :Boolean)
     'contains? (mk-builtin contains? [[:Set :Any] :Any] :Boolean)
     'inc (mk-builtin inc [:Integer] :Integer)
     'dec (mk-builtin dec [:Integer] :Integer)
     'div (mk-builtin quot [:Integer :Integer] :Integer)
     'mod* (mk-builtin mod [:Integer :Integer] :Integer)
     'expt (mk-builtin expt [:Integer :Integer] :Integer)
     'str (mk-builtin str [& :String] :String)
     'subset? (mk-builtin set/subset? [[:Set :Any] [:Set :Any]] :Boolean)
     'sort (mk-builtin sort
                       [:EmptyVec] :EmptyVec
                       [:EmptySet] :EmptyVec
                       [[:Set :Integer]] [:Vec :Integer]
                       [[:Vec :Integer]] [:Vec :Integer])
     'some? (mk-builtin (fn [v] (not= :Unset v))
                        [[:Maybe :Any]] :Boolean)}))

(s/defn ^:private matches-signature?
  [sig :- FnSignature, actual-types :- [HaliteType]]
  (let [{:keys [arg-types variadic-tail]} sig]
    (and
     (<= (count arg-types) (count actual-types))
     (every? true? (map #(subtype? %1 %2) actual-types arg-types))
     (or (and (= (count arg-types) (count actual-types))
              (nil? variadic-tail))
         (every? true? (map #(subtype? %1 variadic-tail)
                            (drop (count arg-types) actual-types)))))))

(s/defn ^:private type-check-fn-application :- HaliteType
  [ctx :- TypeContext, form :- [(s/one BareSymbol :op) s/Any]]
  (let [[op & args] form
        nargs (count args)
        {:keys [signatures impl] :as builtin} (get builtins op)
        actual-types (map (partial type-check* ctx) args)]
    (when (nil? builtin)
      (throw (ex-info (str "function '" op "' not found") {:form form})))

    (loop [[sig & signatures] signatures]
      (cond
        (nil? sig) (throw (ex-info (str "no matching signature for '" (name op) "'")
                                   {:form form}))
        (matches-signature? sig actual-types) (:return-type sig)
        :else (recur signatures)))))

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

(s/defn ^:private type-check-get* :- HaliteType
  [ctx :- TypeContext, form]
  (arg-count-exactly 2 form)
  (let [[_ subexpr index] form
        subexpr-type (type-check* ctx subexpr)]
    (cond
      (subtype? subexpr-type [:Vec :Any])
      (let [index-type (type-check* ctx index)]
        (when (= :EmptyVec subexpr-type)
          (throw (ex-info "Cannot index into empty vector" {:form form})))
        (when (not= :Integer index-type)
          (throw (ex-info "Second argument to get* must be an integer when first argument is a vector"
                          {:form index :expected :Integer :actual-type index-type})))
        (second subexpr-type))

      (spec-type? subexpr-type)
      (let [field-types (->> subexpr-type (lookup-spec (:senv ctx)) :spec-vars)]
        (when-not (and (keyword? index) (bare? index))
          (throw (ex-info "Second argument to get* must be a variable name (as a keyword) when first argument is an instance"
                          {:form form})))
        (when-not (contains? field-types index)
          (throw (ex-info (format "No such variable '%s' on spec '%s'" (name index) subexpr-type)
                          {:form form})))
        (substitute-instance-type (:senv ctx) (get field-types index)))

      :else (throw (ex-info "First argument to get* must be an instance of known type or non-empty vector"
                            {:form form})))))

(s/defn ^:private type-check-equals :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-at-least 2 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (reduce
     (fn [s t]
       (let [m (meet s t)]
         (when (and (not= m s) (not= m t))
           (throw (ex-info (format "Arguments to '%s' have incompatible types" (first expr)) {:form expr})))
         m))
     arg-types))
  :Boolean)

(s/defn ^:private type-check-if :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 3 expr)
  (let [[pred-type s t] (mapv (partial type-check* ctx) (rest expr))
        m (meet s t)]
    (when (not= :Boolean pred-type)
      (throw (ex-info "First argument to 'if' must be boolean" {:form expr})))
    (when (and (not= m s) (not= m t))
      (throw (ex-info "then and else branches to 'if' have incompatible types" {:form expr})))
    m))

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

(s/defn ^:private type-check-quantifier :- HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 2 expr)
  (let [[[sym expr :as bindings] body] (rest expr)]
    (when-not (= 2 (count bindings))
      (throw (ex-info "quantifier binding form must have one variable and one collection"
                      {:form expr})))
    (let [et (elem-type (type-check* ctx expr))]
      (when-not et
        (throw (ex-info "unsupported collection type" {:form expr})))
      (let [pred-type (type-check* (update ctx :tenv extend-scope sym et) body)]
        (when (not= :Boolean pred-type)
          (throw (ex-info "Body expression in A and E must be boolean" {:form expr}))))))
  :Boolean)

(s/defn ^:private type-check-if-value :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 3 expr)
  (let [[sym set-expr unset-expr] (rest expr)]
    (when-not (and (symbol? sym) (bare? sym))
      (throw (ex-info (str "First argument to 'if-value-' must be a bare symbol") {:form expr})))
    (let [sym-type (type-check* ctx sym)
          unset-type (type-check* ctx unset-expr)]
      (when-not (maybe-type? sym-type)
        (throw (ex-info (str "First argument to 'if-value-' must have an optional type")
                        {:form sym :expected [:Maybe :Any] :actual sym-type})))
      (if (= :Unset sym-type)
        (do
          (type-check* (update ctx :tenv extend-scope sym :Any) set-expr)
          unset-type)
        (let [inner-type (second sym-type)
              set-type (type-check* (update ctx :tenv extend-scope sym inner-type) set-expr)
              m (meet set-type unset-type)]
          (when (and (not= m set-type) (not= m unset-type))
            (throw (ex-info (str "then and else branches to 'if-value-' have incompatible types")
                            {:form expr})))
          m)))))

(defn- check-all-sets [[op :as expr] arg-types]
  (when-not (every? #(subtype? % [:Set :Any]) arg-types)
    (throw (ex-info (format "Arguments to '%s' must be sets" op) {:form expr}))))

(s/defn ^:private type-check-union :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (check-all-sets expr arg-types)
    (reduce meet :EmptySet arg-types)))

(s/defn ^:private type-check-intersection :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-at-least 1 expr)
  (let [arg-types (mapv (partial type-check* ctx) (rest expr))]
    (check-all-sets expr arg-types)
    (if (empty? arg-types)
      :EmptySet
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
    (when-not (subtype? arg-type [:Vec :Any])
      (throw (ex-info "Argument to 'first' must be a vector" {:form expr})))
    (when (= :EmptyVec arg-type)
      (throw (ex-info "argument to first is always empty" {:form expr})))
    (second arg-type)))

(s/defn ^:private type-check-rest :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 1 expr)
  (let [arg-type (type-check* ctx (second expr))]
    (when-not (subtype? arg-type [:Vec :Any])
      (throw (ex-info "Argument to 'rest' must be a vector" {:form expr})))
    arg-type))

(s/defn ^:private type-check-conj :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-at-least 2 expr)
  (let [[base-type & elem-types] (mapv (partial type-check* ctx) (rest expr))]
    (when-not (subtype? base-type :Coll)
      (throw (ex-info "First argument to 'conj' must be a set or vector" {:form expr})))
    (let [col-type (if (or (= :EmptyVec base-type) (and (vector? base-type) (= :Vec (first base-type)))) :Vec :Set)]
      (doseq [[elem elem-type] (map vector (drop 2 expr) elem-types)]
        (when (maybe-type? elem-type)
          (throw (ex-info (format "cannot conj possibly unset value to %s" ({:Vec "vector" :Set "set"} col-type))
                          {:form elem}))))
      (reduce meet base-type (map #(vector col-type %) elem-types)))))

(s/defn ^:private type-check-into :- HaliteType
  [ctx :- TypeContext, expr :- s/Any]
  (arg-count-exactly 2 expr)
  (let [[s t] (mapv (partial type-check* ctx) (rest expr))]
    (when-not (subtype? s :Coll)
      (throw (ex-info "First argument to 'into' must be a set or vector" {:form expr})))
    (when-not (subtype? t :Coll)
      (throw (ex-info "Second argument to 'into' must be a set or vector" {:form expr})))
    (when (and (subtype? s [:Vec :Any]) (not (subtype? t [:Vec :Any])))
      (throw (ex-info "When first argument to 'into' is a vector, second argument must also be a vector" {:form expr})))
    (if (#{:EmptySet :EmptyVec} t)
      s
      (let [elem-type (second t)
            col-type (if (or (= :EmptyVec s) (and (vector? s) (= :Vec (first s)))) :Vec :Set)]
        (meet s [col-type elem-type])))))

(s/defn ^:private type-check-refine-to :- HaliteType
  [ctx :- TypeContext, expr]
  (arg-count-exactly 2 expr)
  (let [[subexpr kw] (rest expr)
        s (type-check* ctx subexpr)]
    (when-not (subtype? s :Instance)
      (throw (ex-info "First argument to 'refine-to' must be an instance" {:form expr})))
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

(s/defn ^:private type-check* :- HaliteType
  [ctx :- TypeContext, expr]
  (cond
    (boolean? expr) :Boolean
    (integer? expr) :Integer
    (string? expr) :String
    (symbol? expr) (if (= 'no-value- expr)
                     :Unset
                     (type-check-symbol ctx expr))
    (map? expr) (check-instance type-check* :form ctx expr)
    (seq? expr) (condp = (first expr)
                  'get* (type-check-get* ctx expr)
                  '= (type-check-equals ctx expr)
                  'not= (type-check-equals ctx expr) ; = and not= have same typing rule
                  'if (type-check-if ctx expr)
                  'let (type-check-let ctx expr)
                  'if-value- (type-check-if-value ctx expr)
                  'union (type-check-union ctx expr)
                  'intersection (type-check-intersection ctx expr)
                  'difference (type-check-difference ctx expr)
                  'first (type-check-first ctx expr)
                  'rest (type-check-rest ctx expr)
                  'conj (type-check-conj ctx expr)
                  'into (type-check-into ctx expr)
                  'refine-to (type-check-refine-to ctx expr)
                  'refines-to? (type-check-refines-to? ctx expr)
                  'concrete? (type-check-concrete? ctx expr)
                  'A (type-check-quantifier ctx expr)
                  'E (type-check-quantifier ctx expr)
                  (type-check-fn-application ctx expr))
    (coll? expr) (check-coll type-check* :form ctx expr)
    :else (throw (ex-info "Syntax error" {:form expr}))))

(s/defn type-check :- HaliteType
  "Return the type of the expression, or throw an error if the form is syntactically invalid,
  or not well typed in the given typ environment."
  [senv :- (s/protocol SpecEnv) tenv :- (s/protocol TypeEnv) expr :- s/Any]
  (type-check* {:senv senv :tenv tenv} expr))

(declare eval-expr*)

(s/defn ^:private eval-get* :- s/Any
  [ctx :- EvalContext, target-expr index]
  (let [target (eval-expr* ctx target-expr)]
    (if (vector? target)
      (nth target (dec (eval-expr* ctx index)))
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

(s/defn ^:private eval-quantifier-bools :- [s/Bool]
  [ctx :- EvalContext,
   [[sym coll] pred]]
  (map #(eval-expr* (update ctx :env bind sym %) pred)
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
          (integer? expr)
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
                    'get* (apply eval-get* ctx (rest expr))
                    '= (apply = (map eval-in-env (rest expr)))
                    'not= (apply not= (map eval-in-env (rest expr)))
                    'if (let [[pred then else] (rest expr)]
                          (eval-in-env (if (eval-in-env pred) then else)))
                    'let (apply eval-let ctx (rest expr))
                    'if-value- (let [[sym then else] (rest expr)]
                                 (if (= :Unset (eval-in-env sym))
                                   (eval-in-env else)
                                   (eval-in-env then)))
                    'union (reduce set/union (map eval-in-env (rest expr)))
                    'intersection (reduce set/intersection (map eval-in-env (rest expr)))
                    'difference (apply set/difference (map eval-in-env (rest expr)))
                    'first (or (first (eval-in-env (second expr)))
                               (throw (ex-info "empty vector has no first element" {:form expr})))
                    'rest (let [arg (eval-in-env (second expr))]
                            (if (empty? arg) [] (subvec arg 1)))
                    'conj (apply conj (map eval-in-env (rest expr)))
                    'into (apply into (map eval-in-env (rest expr)))
                    'refine-to (eval-refine-to ctx expr)
                    'refines-to? (let [[subexpr kw] (rest expr)
                                       inst (eval-in-env subexpr)]
                                   (refines-to? inst kw))
                    'concrete? (concrete? (:senv ctx) (eval-in-env (second expr)))
                    'E (boolean (some identity (eval-quantifier-bools ctx (rest expr))))
                    'A (every? identity (eval-quantifier-bools ctx (rest expr)))
                    (apply (:impl (get builtins (first expr)))
                           (map eval-in-env (rest expr))))
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
