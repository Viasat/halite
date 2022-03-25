;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns jibe.halite.envs
  "Halite spec, type, and eval environment abstractions."
  (:require [jibe.halite.types :refer [BareKeyword BareSymbol NamespacedKeyword HaliteType]]
            [schema.core :as s]))

(s/defschema SpecInfo
  {:spec-vars {BareKeyword HaliteType}
   :constraints [[(s/one s/Str :name) (s/one s/Any :expr)]]
   :refines-to {NamespacedKeyword {:clauses [[(s/one s/Str :name) (s/one s/Any :expr)]]
                                   (s/optional-key :inverted?) s/Bool}}})

(defprotocol SpecEnv
  (lookup-spec* [self spec-id]))

(s/defn lookup-spec :- (s/maybe SpecInfo)
  "Look up the spec with the given id in the given type environment, returning variable type information.
  Returns nil when the spec is not found."
  [senv :- (s/protocol SpecEnv), spec-id :- NamespacedKeyword]
  (lookup-spec* senv spec-id))

(defprotocol TypeEnv
  (scope* [self])
  (extend-scope* [self sym t]))

(s/defn scope :- {BareSymbol HaliteType}
  "The scope of th current type environment."
  [tenv :- (s/protocol TypeEnv)]
  (scope* tenv))

(s/defn extend-scope :- (s/protocol TypeEnv)
  "Produce a new type environment, extending the current scope by mapping the given symbol to the given type."
  [tenv :- (s/protocol TypeEnv), sym :- BareSymbol, t :- HaliteType]
  (extend-scope* tenv sym t))

(defprotocol Env
  (bindings* [self])
  (bind* [self sym value]))

(s/defn bindings :- {BareSymbol s/Any}
  "The bindings of the current environment."
  [env :- (s/protocol Env)]
  (bindings* env))

(s/defn bind :- (s/protocol Env)
  "The environment produced by extending env with sym mapped to value."
  [env :- (s/protocol Env), sym :- BareSymbol, value :- s/Any]
  (bind* env sym value))


(deftype TypeEnvImpl [scope]
  TypeEnv
  (scope* [self] scope)
  (extend-scope* [self sym t] (TypeEnvImpl. (assoc scope sym t))))

(s/defn type-env :- (s/protocol TypeEnv)
  [scope :- {BareSymbol HaliteType}]
  (->TypeEnvImpl scope))

(s/defn type-env-from-spec :- (s/protocol TypeEnv)
  "Return a type environment where spec lookups are delegated to tenv, but the in-scope symbols
  are the variables of the given resource spec."
  [spec :- SpecInfo]
  (let [{:keys [spec-vars]} spec]
    (->TypeEnvImpl
     (zipmap (map symbol (keys spec-vars))
             (vals spec-vars)))))

(deftype EnvImpl [bindings]
  Env
  (bindings* [self] bindings)
  (bind* [self sym value] (EnvImpl. (assoc bindings sym value))))

(s/defn env :- (s/protocol Env)
  [bindings :- {BareSymbol s/Any}]
  (->EnvImpl bindings))

(s/defn env-from-inst :- (s/protocol Env)
  [spec-info :- SpecInfo, inst]
  (->EnvImpl
   (reduce
    (fn [m kw]
      (assoc m (symbol kw) (if (contains? inst kw) (kw inst) :Unset)))
    {}
    (keys (:spec-vars spec-info)))))
