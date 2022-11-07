;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.envs
  "Halite spec, type, and eval environment abstractions."
  (:require [clojure.set :as set]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.types :as types]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defschema Refinement
  {:expr s/Any
   (s/optional-key :name) s/Str
   (s/optional-key :inverted?) s/Bool})

(s/defschema NamedConstraint
  [(s/one base/ConstraintName :name) (s/one s/Any :expr)])

(s/defschema RefinesTo {types/NamespacedKeyword Refinement})

(s/defschema ConstraintMap {base/ConstraintName s/Any})

(s/defschema SpecInfo
  {(s/optional-key :spec-vars) {types/BareKeyword types/HaliteType}
   (s/optional-key :constraints) ConstraintMap
   (s/optional-key :refines-to) {types/NamespacedKeyword Refinement}
   (s/optional-key :abstract?) s/Bool})

(defprotocol SpecEnv
  (lookup-spec* [self spec-id]))

(s/defn lookup-spec :- (s/maybe SpecInfo)
  "Look up the spec with the given id in the given type environment, returning variable type information.
  Returns nil when the spec is not found."
  [senv :- (s/protocol SpecEnv), spec-id :- types/NamespacedKeyword]
  (lookup-spec* senv spec-id))

(deftype SpecEnvImpl [spec-info-map]
  SpecEnv
  (lookup-spec* [self spec-id] (spec-info-map spec-id)))

(s/defn spec-env :- (s/protocol SpecEnv)
  [spec-info-map :- {types/NamespacedKeyword SpecInfo}]
  (->SpecEnvImpl spec-info-map))

(defprotocol TypeEnv
  (scope* [self])
  (extend-scope* [self sym t]))

(s/defn scope :- {types/BareSymbol types/HaliteType}
  "The scope of the current type environment."
  [tenv :- (s/protocol TypeEnv)]
  (scope* tenv))

(s/defn extend-scope :- (s/protocol TypeEnv)
  "Produce a new type environment, extending the current scope by mapping the given symbol to the given type."
  [tenv :- (s/protocol TypeEnv), sym :- types/BareSymbol, t :- types/HaliteType]
  (extend-scope* tenv sym t))

(defprotocol Env
  (bindings* [self])
  (bind* [self sym value]))

(s/defn bindings :- {types/BareSymbol s/Any}
  "The bindings of the current environment."
  [env :- (s/protocol Env)]
  (bindings* env))

(s/defn bind :- (s/protocol Env)
  "The environment produced by extending env with sym mapped to value."
  [env :- (s/protocol Env), sym :- types/BareSymbol, value :- s/Any]
  (bind* env sym value))

(deftype TypeEnvImpl [scope]
  TypeEnv
  (scope* [self] scope)
  (extend-scope* [self sym t] (TypeEnvImpl. (assoc scope sym t))))

(s/defn type-env :- (s/protocol TypeEnv)
  [scope :- {types/BareSymbol types/HaliteType}]
  (->TypeEnvImpl (merge '{;; for backwards compatibility
                          no-value :Unset}
                        scope)))

(s/defn type-env-from-spec :- (s/protocol TypeEnv)
  "Return a type environment where spec lookups are delegated to tenv, but the in-scope symbols
  are the variables of the given resource spec."
  [senv :- (s/protocol SpecEnv)
   spec :- SpecInfo]
  (-> spec
      :spec-vars
      (update-keys symbol)
      (type-env)))

(deftype EnvImpl [bindings]
  Env
  (bindings* [self] bindings)
  (bind* [self sym value] (EnvImpl. (assoc bindings sym value))))

(s/defn env :- (s/protocol Env)
  [bindings :- {types/BareSymbol s/Any}]
  (->EnvImpl (merge '{;; for backwards compatibility
                      no-value :Unset} bindings)))

(s/defn env-from-inst :- (s/protocol Env)
  [spec-info :- SpecInfo, inst]
  (env
   (reduce
    (fn [m kw]
      (assoc m (symbol kw) (if (contains? inst kw) (kw inst) :Unset)))
    {}
    (keys (:spec-vars spec-info)))))

;;;;;; Spec Maps ;;;;;;;;;;;;

;; The SpecEnv protocol probably needs to be eliminated. What value does it actually provide?
;; Meanwhile, it gets in the way whenever we need to manipulate sets of SpecInfo values,
;; like in the propagate namespace.

;; Maybe this is the direction we should head in for replacing SpecEnv more generally?

;; DM (talking with CH): SpecEnv is providing value to apps that do not want to proactively load
;; all specs into a map (e.g. they are stored in a database). So the thought is that we keep
;; SpecEnv and we keep most halite operations simply performing lookups of the specs they need
;; however, for operations that require enumarating all specs (such as a global check for dependency
;; cycles), require that users provide a SpecMap instead of a SpecEnv. This way applications can
;; opt-in to the operations they want to use and deal with the implications of their choice

(s/defschema SpecMap
  {types/NamespacedKeyword SpecInfo})

(defn- spec-ref-from-type [htype]
  (cond
    (and (keyword? htype) (namespace htype)) htype
    (vector? htype) (recur (second htype))
    :else nil))

(defn- spec-refs-from-expr
  [expr]
  (cond
    (integer? expr) #{}
    (boolean? expr) #{}
    (symbol? expr) #{}
    (string? expr) #{}
    (map? expr) (->> (dissoc expr :$type) vals (map spec-refs-from-expr) (apply set/union #{(:$type expr)}))
    (vector? expr) (reduce set/union #{} (map spec-refs-from-expr expr))
    (seq? expr) (let [[op & args] expr]
                  (condp = op
                    'let (let [[bindings body] args]
                           (->> bindings (partition 2) (map (comp spec-refs-from-expr second))
                                (apply set/union (spec-refs-from-expr body))))
                    'get (spec-refs-from-expr (first args))
                    'get* (spec-refs-from-expr (first args))
                    'refine-to (spec-refs-from-expr (first args))
                    (apply set/union (map spec-refs-from-expr args))))
    :else (throw (ex-info "BUG! Can't extract spec refs from form" {:form expr}))))

(s/defn spec-refs :- #{types/NamespacedKeyword}
  "The set of spec ids referenced by the given spec, as variable types or in constraint/refinement expressions."
  [{:keys [spec-vars refines-to constraints] :as spec-info} :- SpecInfo]
  (->> spec-vars
       vals
       (map spec-ref-from-type)
       (remove nil?)
       (concat (keys refines-to))
       set
       (set/union
        (->> constraints (map (comp spec-refs-from-expr second)) (apply set/union)))
       (set/union
        (->> refines-to vals (map (comp spec-refs-from-expr :expr)) (apply set/union)))))

(s/defn build-spec-map :- SpecMap
  "Return a map of spec-id to SpecInfo, for all specs in senv reachable from the identified root spec."
  [senv :- (s/protocol SpecEnv), root-spec-id :- types/NamespacedKeyword]
  (loop [spec-map {}
         next-spec-ids [root-spec-id]]
    (if-let [[spec-id & next-spec-ids] next-spec-ids]
      (if (contains? spec-map spec-id)
        (recur spec-map next-spec-ids)
        (let [spec-info (lookup-spec senv spec-id)]
          (recur
           (assoc spec-map spec-id spec-info)
           (into next-spec-ids (spec-refs spec-info)))))
      spec-map)))

;; Ensure that a SpecMap can be used anywhere a SpecEnv can.
(extend-type clojure.lang.IPersistentMap
  SpecEnv
  (lookup-spec* [spec-map spec-id]
    (when-let [spec (get spec-map spec-id)]
      spec)))

(defn init
  "Here to load the clojure map protocol extension above"
  [])
