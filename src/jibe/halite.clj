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

(s/defschema TypeAtom
  "Type atoms are always keywords. Namespace-qualified keywords are interpreted as spec ids.
  Unqualified keywords identify built-in scalar types."
  (s/conditional
   #(and (keyword? %) (namespaced? %)) NamespacedKeyword
   :else (s/enum :Integer :String :Boolean :EmptySet :EmptyVec)))

(s/defschema HaliteType
  "A Halite type is either a type atom (keyword), or a collection type."
  (s/cond-pre
   TypeAtom
   [(s/one (s/enum :Set :Vec) "coll-type") (s/one (s/recursive #'HaliteType) "elem-type")]))

(s/defn compatible :- (s/maybe HaliteType)
  "If types t1 and t2 are compatible, return the more concrete type. Otherwise return nil."
  ([] nil)
  ([t :- HaliteType] t)
  ([t1 :- HaliteType, t2 :- HaliteType]
   (cond
     (and (= :EmptySet t1) (vector? t2) (= :Set (first t2))) t2
     (and (= :EmptyVec t1) (vector? t2) (= :Vec (first t2))) t2
     (and (= :EmptySet t2) (vector? t1) (= :Set (first t1))) t1
     (and (= :EmptyVec t2) (vector? t1) (= :Vec (first t1))) t1
     (and (vector? t1) (vector? t2) (= (first t1) (first t2))) (when-let [elem-type (compatible (second t1) (second t2))]
                                                                 [(first t1) elem-type])
     (= t1 t2) t1
     :else nil)))

(s/defschema TypeEnv
  "A type environment.

  The :specs entry maps spec ids to fields to types.
  The :vars entry maps variables to types.
  The :refinesTo* entry encodes the transitive closure of the refinement graph.
  "
  {:specs {NamespacedKeyword {BareKeyword HaliteType}}
   :vars {BareSymbol HaliteType}
   :refinesTo* {NamespacedKeyword #{NamespacedKeyword}}})

(declare type-check)

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
        (when (nil? (compatible actual-type field-type))
          (throw (ex-info (str "value of " field-kw " has wrong type")
                          {:form inst :variable field-kw :expected field-type :actual actual-type})))))
    t))

(s/defn ^:private type-check-coll :- HaliteType
  [tenv :- TypeEnv, coll]
  (let [coll-type (cond
                    (vector? coll) :Vec
                    (set? coll) :Set
                    :else (throw (ex-info "Invalid value" {:form coll})))
        inner-type (->> coll (map (partial type-check tenv)) (reduce compatible))]
    (cond
      (= [] coll) :EmptyVec
      (= #{} coll) :EmptySet
      :else (if (nil? inner-type)
              (throw (ex-info (str (if (vector? coll) "vector" "set") " elements must be of same type") {:form coll}))
              [coll-type inner-type]))))

(s/defn type-check :- HaliteType
  "Return the type of the expression, or throw an error if the form is syntactically invalid,
  or not well typed in the given typ environment."
  [tenv :- TypeEnv, expr :- s/Any]
  (cond
    (boolean? expr) :Boolean
    (integer? expr) :Integer
    (string? expr) :String
    (map? expr) (type-check-instance tenv expr)
    (coll? expr) (type-check-coll tenv expr)))

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

(s/defn eval-expr :- s/Any
  [env expr]
  (cond
    (or (boolean? expr)
        (integer? expr)
        (string? expr)) expr
    (map? expr) (->> (dissoc expr :$type)
                     (map (fn [[k v]] [k (eval-expr env v)]))
                     (into (select-keys expr [:$type])))
    (vector? expr) (mapv (partial eval-expr env) expr)
    (set? expr) (set (map (partial eval-expr env) expr))))
