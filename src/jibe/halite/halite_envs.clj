;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.envs
  "Halite spec, type, and eval environment abstractions."
  (:require [jibe.halite.types :as halite-types]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defschema Refinement
  {:expr s/Any
   (s/optional-key :inverted?) s/Bool})

(s/defschema SpecInfo
  {:spec-vars {halite-types/BareKeyword halite-types/HaliteType}
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

(s/defn type-env-from-spec :- (s/protocol TypeEnv)
  "Return a type environment where spec lookups are delegated to tenv, but the in-scope symbols
  are the variables of the given resource spec."
  [senv :- (s/protocol SpecEnv), spec :- SpecInfo]
  (let [{:keys [spec-vars]} spec]
    (->TypeEnvImpl
     (update-keys spec-vars symbol))))

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
