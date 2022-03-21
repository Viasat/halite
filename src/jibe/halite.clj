;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite
  "Expression language for resource spec constraints and refinements that is almost, but
  not quite, a drop-in replacement for salt."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [schema.core :as s]))

(defn- bare? [sym-or-kw] (nil? (namespace sym-or-kw)))
(def ^:private namespaced? (complement bare?))

(s/defschema ^:private BareKeyword (s/constrained s/Keyword bare?))
(s/defschema ^:private NamespacedKeyword (s/constrained s/Keyword namespaced?))
(s/defschema ^:private BareSymbol (s/constrained s/Symbol bare?))

(defn- spec-type? [t] (and (keyword? t) (namespaced? t)))

(s/defschema TypeAtom
  "Type atoms are always keywords. Namespace-qualified keywords are interpreted as spec ids.
  Unqualified keywords identify built-in scalar types."
  (s/conditional
   spec-type? NamespacedKeyword
   :else (s/enum :Integer :String :Boolean :EmptySet :EmptyVec :Coll :Any)))

(s/defschema HaliteType
  "A Halite type is either a type atom (keyword), or a collection type."
  (s/cond-pre
   TypeAtom
   [(s/one (s/enum :Set :Vec) "coll-type") (s/one (s/recursive #'HaliteType) "elem-type")]))

(s/defn ^:private subtype? :- s/Bool
  [s :- HaliteType, t :- HaliteType]
  (or
    (= s t) ; the subtyping relation is reflexive
    (= t :Any) ; :Any is the 'top' type
    (and (= t :Coll) (boolean (#{:EmptyVec :EmptySet} s)))
    (and (= s :EmptyVec) (vector? t) (= :Vec (first t)))
    (and (= s :EmptySet) (vector? t) (= :Set (first t)))
    (and (vector? s) (vector? t) (= (first s) (first t)) (subtype? (second s) (second t)))
    (and (vector? s) (= t :Coll))))

(s/defn ^:private meet :- HaliteType
  "The 'least' supertype of m and t. Formally, return the type m such that all are true:
    (subtype? s m)
    (subtype? t m)
    For all types l, (implies (and (subtype? s l) (subtype? t l)) (subtype? l m))"
  [s :- HaliteType, t :- HaliteType]
  (cond
    (subtype? s t) t
    (subtype? t s) s
    (and (vector? s) (vector? t) (= (first s) (first t))) [(first s) (meet (second s) (second t))]
    (and (subtype? s :Coll) (subtype? t :Coll)) :Coll
    :else :Any))

(s/defschema TypeEnv
  "A type environment.

  The :specs entry maps spec ids to fields to types.
  The :vars entry maps variables to types.
  The :refinesTo* entry encodes the transitive closure of the refinement graph.
  "
  {:specs {NamespacedKeyword {BareKeyword HaliteType}}
   :vars {BareSymbol HaliteType}
   :refinesTo* {NamespacedKeyword #{NamespacedKeyword}}})

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
     'and (mk-builtin (fn [& args] (every? true? args))
                      [:Boolean & :Boolean] :Boolean )
     'or (mk-builtin (fn [& args] (true? (some true? args)))
                     [:Boolean & :Boolean] :Boolean)
     }))

(declare type-check)

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
  [tenv :- TypeEnv, form :- [(s/one BareSymbol :op) s/Any]]
  (let [[op & args] form
        nargs (count args)
        {:keys [signatures impl] :as builtin} (get builtins op)
        actual-types (map (partial type-check tenv) args)]
    (when (nil? builtin)
      (throw (ex-info (str "function '" op "' not found") {:form form})))

    (loop [[sig & signatures] signatures]
      (cond
        (nil? sig) (throw (ex-info (str "no matching signature for '" (name op) "'")
                                   {:form form}))
        (matches-signature? sig actual-types) (:return-type sig)
        :else (recur signatures)))))

(s/defn ^:private type-check-instance :- NamespacedKeyword
  [tenv :- TypeEnv, inst :- {s/Keyword s/Any}]
  (let [t (:$type inst)
        field-types (-> tenv :specs (get t))]
    (when (nil? t)
      (throw (ex-info "instance literal must have :$type field" {:form inst})))

    (when-not (and (keyword? t) (namespaced? t))
      (throw (ex-info "expected namespaced keyword as value of :$type" {:form inst})))

    (when (nil? field-types)
      (throw (ex-info (str "resource spec not found: " t) {:form inst})))

    (let [required-fields (set (keys field-types))
          supplied-fields (disj (set (keys inst)) :$type)
          missing-fields (set/difference required-fields supplied-fields)
          invalid-fields (set/difference supplied-fields required-fields)]
      (when (seq missing-fields)
        (throw (ex-info (str "missing required variables: " (str/join "," missing-fields))
                        {:form inst :missing-vars missing-fields})))
      (when (seq invalid-fields)
        (throw (ex-info (str "variables not defined on spec: " (str/join "," invalid-fields))
                        {:form inst :invalid-vars invalid-fields}))))

    (doseq [[field-kw field-type] field-types]
      (let [field-val (get inst field-kw)
            actual-type (type-check tenv field-val)]
        (when-not (subtype? actual-type field-type)
          (throw (ex-info (str "value of " field-kw " has wrong type")
                          {:form inst :variable field-kw :expected field-type :actual actual-type})))))
    t))

(s/defn ^:private type-check-coll :- HaliteType
  [tenv :- TypeEnv, coll]
  (cond
    (= [] coll) :EmptyVec
    (= #{} coll) :EmptySet
    :else (let [elem-types (map (partial type-check tenv) coll)]
            [(cond
               (vector? coll) :Vec
               (set? coll) :Set
               :else (throw (ex-info "Invalid value" {:form coll})))
             (condp = (count coll)
               0 nil
               1 (first elem-types)
               (reduce meet elem-types))])))

(s/defn ^:private type-check-symbol :- HaliteType
  [tenv :- TypeEnv, sym]
  (or (get-in tenv [:vars sym])
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
                            (name (first form)) n (count (rest form)))))))

(s/defn ^:private type-check-get* :- HaliteType
  [tenv :- TypeEnv, form]
  (arg-count-exactly 2 form)
  (let [[_ subexpr index] form
        subexpr-type (type-check tenv subexpr)]
    (cond
      (subtype? subexpr-type [:Vec :Any])
      (let [index-type (type-check tenv index)]
        (when (= :EmptyVec subexpr-type)
          (throw (ex-info "Cannot index into empty vector" {:form form})))
        (when (not= :Integer index-type)
          (throw (ex-info "Second argument to get* must be an integer when first argument is a vector"
                          {:form index :expected :Integer :actual-type index-type})))
        (second subexpr-type))

      (spec-type? subexpr-type)
      (let [field-types (get-in tenv [:specs subexpr-type])]
        (when-not (and (keyword? index) (bare? index))
          (throw (ex-info "Second argument to get* must be a variable name (as a keyword) when first argument is an instance"
                          {:form form})))
        (when-not (contains? field-types index)
          (throw (ex-info (format "No such variable '%s' on spec '%s'" (name index) subexpr-type)
                          {:form form})))
        (get field-types index))

      :else (throw (ex-info "First argument to get* must be an instance or non-empty vector"
                            {:form form})))))

(s/defn type-check-equals :- HaliteType
  [tenv :- TypeEnv, expr :- s/Any]
  (arg-count-at-least 2 expr)
  (let [arg-types (mapv (partial type-check tenv) (rest expr))]
    (reduce
     (fn [s t]
       (let [m (meet s t)]
         (when (and (not= m s) (not= m t))
           (throw (ex-info "Arguments to '=' have incompatible types" {:form expr})))
         m))
     arg-types))
  :Boolean)

(s/defn ^:private type-check-if :- HaliteType
  [tenv :- TypeEnv, expr :- s/Any]
  (arg-count-exactly 3 expr)
  (let [[pred-type s t] (mapv (partial type-check tenv) (rest expr))
        m (meet s t)]
    (when (not= :Boolean pred-type)
      (throw (ex-info "First argument to 'if' must be boolean" {:form expr})))
    (when (and (not= m s) (not= m t))
      (throw (ex-info "then and else branches to 'if' have incompatible types" {:form expr})))
    m))

(s/defn type-check :- HaliteType
  "Return the type of the expression, or throw an error if the form is syntactically invalid,
  or not well typed in the given typ environment."
  [tenv :- TypeEnv, expr :- s/Any]
  (cond
    (boolean? expr) :Boolean
    (integer? expr) :Integer
    (string? expr) :String
    (symbol? expr) (type-check-symbol tenv expr)
    (map? expr) (type-check-instance tenv expr)
    (list? expr) (condp = (first expr)
                   'get* (type-check-get* tenv expr)
                   '= (type-check-equals tenv expr)
                   'if (type-check-if tenv expr)
                   (type-check-fn-application tenv expr))
    (coll? expr) (type-check-coll tenv expr)
    :else (throw (ex-info "Syntax error" {:form expr}))))

(def Env
  "An environment in which expressions may be evaluated.
  Compreses a type environment together with

  a :bindings entry that maps variables to values
  a :refinesTo entry representing a refinement graph"
  (merge TypeEnv
         {:bindings {BareSymbol s/Any}
          :refinesTo {NamespacedKeyword
                      {NamespacedKeyword
                       [{:guard s/Any
                         :expr s/Any}]}}}))

(declare eval-expr)

(s/defn ^:private eval-get* :- s/Any
  [env target-expr index]
  (let [target (eval-expr env target-expr)]
    (if (vector? target)
      (nth target (eval-expr env index))
      (get target index))))

(s/defn eval-expr :- s/Any
  [env :- Env, expr]
  (cond
    (or (boolean? expr)
        (integer? expr)
        (string? expr)) expr
    (symbol? expr) (get-in env [:bindings expr])
    (map? expr) (->> (dissoc expr :$type)
                     (map (fn [[k v]] [k (eval-expr env v)]))
                     (into (select-keys expr [:$type])))
    (list? expr) (condp = (first expr)
                   'get* (apply eval-get* env (rest expr))
                   '= (apply = (map (partial eval-expr env) (rest expr)))
                   'if (let [[pred then else] (rest expr)]
                         (if (eval-expr env pred)
                           (eval-expr env then)
                           (eval-expr env else)))
                   (apply (:impl (get builtins (first expr)))
                          (map (partial eval-expr env) (rest expr))))
    (vector? expr) (mapv (partial eval-expr env) expr)
    (set? expr) (set (map (partial eval-expr env) expr))))
