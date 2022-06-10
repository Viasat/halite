;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.halite-envs
  "Halite spec, type, and eval environment abstractions."
  (:require [clojure.string :as str]
            [jibe.halite.halite-types :as halite-types]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defschema Refinement
  {:expr s/Any
   (s/optional-key :inverted?) s/Bool})

(def primitive-types ["String" "Integer"
                      "Decimal1" "Decimal2" "Decimal3" "Decimal4" "Decimal5"
                      "Decimal6" "Decimal7" "Decimal8" "Decimal9" "Decimal10"
                      "Decimal11" "Decimal12" "Decimal13" "Decimal14" "Decimal15"
                      "Decimal16" "Decimal17" "Decimal18"
                      "Boolean"])

(s/defschema MandatoryVarType
  (s/conditional
   string? (apply s/enum primitive-types)
   vector? (s/constrained [(s/recursive #'MandatoryVarType)]
                          #(= 1 (count %))
                          "exactly one inner type")
   set? (s/constrained #{(s/recursive #'MandatoryVarType)}
                       #(= 1 (count %))
                       "exactly one inner type")
   :else halite-types/NamespacedKeyword))

(s/defschema VarType
  (s/conditional
   #(and (vector? %) (= :Maybe (first %)))
   [(s/one (s/enum :Maybe) :maybe) (s/one MandatoryVarType :inner)]

   :else MandatoryVarType))

(s/defn optional-var-type? :- s/Bool
  [var-type :- VarType]
  (and (vector? var-type) (= :Maybe (first var-type))))

(s/defschema SpecInfo
  {:spec-vars {halite-types/BareKeyword VarType}
   :constraints [[(s/one s/Str :name) (s/one s/Any :expr)]]
   :refines-to {halite-types/NamespacedKeyword Refinement}
   (s/optional-key :abstract?) s/Bool})

(defprotocol SpecEnv
  (lookup-spec* [self spec-id]))

(s/defn lookup-spec :- (s/maybe SpecInfo)
  "Look up the spec with the given id in the given type environment, returning variable type information.
  Returns nil when the spec is not found."
  [senv :- (s/protocol SpecEnv), spec-id :- halite-types/NamespacedKeyword]
  (lookup-spec* senv spec-id))

(deftype SpecEnvImpl [spec-info-map]
  SpecEnv
  (lookup-spec* [self spec-id] (spec-info-map spec-id)))

(s/defn spec-env :- (s/protocol SpecEnv)
  [spec-info-map :- {halite-types/NamespacedKeyword SpecInfo}]
  (->SpecEnvImpl spec-info-map))

(defprotocol TypeEnv
  (scope* [self])
  (extend-scope* [self sym t]))

(s/defn scope :- {halite-types/BareSymbol halite-types/HaliteType}
  "The scope of the current type environment."
  [tenv :- (s/protocol TypeEnv)]
  (scope* tenv))

(s/defn extend-scope :- (s/protocol TypeEnv)
  "Produce a new type environment, extending the current scope by mapping the given symbol to the given type."
  [tenv :- (s/protocol TypeEnv), sym :- halite-types/BareSymbol, t :- halite-types/HaliteType]
  (extend-scope* tenv sym t))

(defprotocol Env
  (bindings* [self])
  (bind* [self sym value]))

(s/defn bindings :- {halite-types/BareSymbol s/Any}
  "The bindings of the current environment."
  [env :- (s/protocol Env)]
  (bindings* env))

(s/defn bind :- (s/protocol Env)
  "The environment produced by extending env with sym mapped to value."
  [env :- (s/protocol Env), sym :- halite-types/BareSymbol, value :- s/Any]
  (bind* env sym value))

(deftype TypeEnvImpl [scope]
  TypeEnv
  (scope* [self] scope)
  (extend-scope* [self sym t] (TypeEnvImpl. (assoc scope sym t))))

(s/defn type-env :- (s/protocol TypeEnv)
  [scope :- {halite-types/BareSymbol halite-types/HaliteType}]
  (->TypeEnvImpl scope))

(s/defn halite-type-from-var-type :- halite-types/HaliteType
  [senv :- (s/protocol SpecEnv), var-type :- VarType]
  (when (coll? var-type)
    (let [n (if (and (vector? var-type) (= :Maybe (first var-type))) 2 1)]
      (when (not= n (count var-type))
        (throw (ex-info "Must contain exactly one inner type"
                        {:var-type var-type})))))
  (let [check-mandatory #(if (optional-var-type? %)
                           (throw (ex-info "Set and vector types cannot have optional inner types"
                                           {:var-type %}))
                           %)]
    (cond
      (string? var-type)
      (cond
        (#{"Integer" "String" "Boolean"} var-type) (keyword var-type)
        (str/starts-with? var-type "Decimal") (halite-types/decimal-type (Long/parseLong (subs var-type 7)))
        :default (throw (ex-info (format "Unrecognized primitive type: %s" var-type) {:var-type var-type})))

      (optional-var-type? var-type)
      [:Maybe (halite-type-from-var-type senv (second var-type))]

      (vector? var-type)
      [:Vec (halite-type-from-var-type senv (check-mandatory (first var-type)))]

      (set? var-type)
      [:Set (halite-type-from-var-type senv (check-mandatory (first var-type)))]

      (halite-types/namespaced-keyword? var-type)
      (if-let [spec-info (lookup-spec senv var-type)]
        (if (:abstract? spec-info)
          (halite-types/abstract-spec-type var-type)
          (halite-types/concrete-spec-type var-type))
        (throw (ex-info (format "Spec not found: %s" var-type) {:var-type var-type})))

      :else (throw (ex-info "Invalid spec variable type" {:var-type var-type})))))

(s/defn type-env-from-spec :- (s/protocol TypeEnv)
  "Return a type environment where spec lookups are delegated to tenv, but the in-scope symbols
  are the variables of the given resource spec."
  [senv :- (s/protocol SpecEnv), spec :- SpecInfo]
  (-> spec
      :spec-vars
      (update-keys symbol)
      (update-vals (partial halite-type-from-var-type senv))
      (->TypeEnvImpl)))

(deftype EnvImpl [bindings]
  Env
  (bindings* [self] bindings)
  (bind* [self sym value] (EnvImpl. (assoc bindings sym value))))

(s/defn env :- (s/protocol Env)
  [bindings :- {halite-types/BareSymbol s/Any}]
  (->EnvImpl bindings))

(s/defn env-from-inst :- (s/protocol Env)
  [spec-info :- SpecInfo, inst]
  (->EnvImpl
   (reduce
    (fn [m kw]
      (assoc m (symbol kw) (if (contains? inst kw) (kw inst) :Unset)))
    {}
    (keys (:spec-vars spec-info)))))
