;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.envs
  "Halite spec, type, and eval environment abstractions."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.types :as types]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(s/defschema Refinement
  {:expr s/Any
   (s/optional-key :name) s/Str
   (s/optional-key :inverted?) s/Bool})

(def primitive-types (into ["String" "Integer"
                            "Boolean"]
                           (map #(str "Decimal" %) (range 1 (inc fixed-decimal/max-scale)))))

(s/defschema MandatoryVarType
  (s/conditional
   string? (apply s/enum primitive-types)
   vector? (s/constrained [(s/recursive #'MandatoryVarType)]
                          #(= 1 (count %))
                          "exactly one inner type")
   set? (s/constrained #{(s/recursive #'MandatoryVarType)}
                       #(= 1 (count %))
                       "exactly one inner type")
   :else types/NamespacedKeyword))

(defn maybe-type? [t]
  (and (vector? t) (= :Maybe (first t))))

(defn no-maybe [t]
  (if (maybe-type? t)
    (second t)
    t))

(s/defschema VarType
  (s/conditional
   maybe-type?
   [(s/one (s/enum :Maybe) :maybe) (s/one MandatoryVarType :inner)]

   :else MandatoryVarType))

(s/defn optional-var-type? :- s/Bool
  [var-type :- VarType]
  (and (vector? var-type) (= :Maybe (first var-type))))

(s/defschema NamedConstraint
  [(s/one base/ConstraintName :name) (s/one s/Any :expr)])

(s/defschema SpecVars {types/BareKeyword VarType})

(s/defschema RefinesTo {types/NamespacedKeyword Refinement})

(s/defschema ConstraintMap {base/ConstraintName s/Any})

(s/defschema HaliteSpecInfo
  {(s/optional-key :spec-vars) {types/BareKeyword types/HaliteType}
   (s/optional-key :constraints) ConstraintMap
   (s/optional-key :refines-to) {types/NamespacedKeyword Refinement}
   (s/optional-key :abstract?) s/Bool})

(s/defschema UserSpecInfo
  {(s/optional-key :spec-vars) {types/BareKeyword VarType}
   (s/optional-key :constraints) {base/UserConstraintName s/Any}
   (s/optional-key :refines-to) {types/NamespacedKeyword Refinement}
   (s/optional-key :abstract?) s/Bool})

(defprotocol SpecEnv
  (lookup-spec* [self spec-id]))

(s/defn lookup-spec :- (s/maybe HaliteSpecInfo)
  "Look up the spec with the given id in the given type environment, returning variable type information.
  Returns nil when the spec is not found."
  [senv :- (s/protocol SpecEnv), spec-id :- types/NamespacedKeyword]
  (lookup-spec* senv spec-id))

(s/defn lookup-spec-abstract? :- (s/maybe Boolean)
  "Lookup whether the given spec is abstract. Produce nil if the spec does not exist. This function
  avoids enforcing a schema on the spec specifically so that it can be used when the spec
  environment is being converted between formats."
  [senv :- (s/protocol SpecEnv), spec-id :- types/NamespacedKeyword]
  (when-let [spec (lookup-spec* senv spec-id)]
    (boolean (:abstract? spec))))

(declare to-halite-spec)

(s/defn halite-lookup-spec :- (s/maybe HaliteSpecInfo)
  "Look up the spec with the given id in the given type environment, returning variable type information.
  Returns nil when the spec is not found."
  [senv :- (s/protocol SpecEnv), spec-id :- types/NamespacedKeyword]
  (to-halite-spec senv (lookup-spec* senv spec-id)))

(deftype SpecEnvImpl [spec-info-map]
  SpecEnv
  (lookup-spec* [self spec-id] (spec-info-map spec-id)))

(s/defn spec-env :- (s/protocol SpecEnv)
  [spec-info-map :- {types/NamespacedKeyword HaliteSpecInfo}]
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

(s/defn halite-type-from-var-type :- types/HaliteType
  [senv :- (s/protocol SpecEnv)
   var-type :- VarType]
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
        (str/starts-with? var-type "Decimal") (types/decimal-type (Long/parseLong (subs var-type 7)))
        :default (throw (ex-info (format "Unrecognized primitive type: %s" var-type) {:var-type var-type})))

      (optional-var-type? var-type)
      [:Maybe (halite-type-from-var-type senv (second var-type))]

      (vector? var-type)
      [:Vec (halite-type-from-var-type senv (check-mandatory (first var-type)))]

      (set? var-type)
      [:Set (halite-type-from-var-type senv (check-mandatory (first var-type)))]

      (types/namespaced-keyword? var-type)
      (let [abstract? (lookup-spec-abstract? senv var-type)]
        (if (nil? abstract?)
          (throw (ex-info (format "Spec not found: %s" var-type) {:var-type var-type}))
          (if abstract?
            (types/abstract-spec-type var-type)
            (types/concrete-spec-type var-type))))

      :else (throw (ex-info "Invalid spec variable type" {:var-type var-type})))))

(s/defn halite-type-from-var-type-if-needed :- types/HaliteType
  [senv :- (s/protocol SpecEnv)
   var-type :- s/Any]

  ;; TODO: remove this once we are swtiched over to use halite types pervasively
  (if (nil? (s/check types/HaliteType var-type))
    var-type
    (halite-type-from-var-type senv var-type)))

(s/defn to-halite-spec :- (s/maybe HaliteSpecInfo)
  "Create specs with halite types from specs with var types"
  [senv :- (s/protocol SpecEnv)
   spec-info :- s/Any]
  (when spec-info
    (if (seq (:spec-vars spec-info))
      (update spec-info :spec-vars update-vals (partial halite-type-from-var-type-if-needed senv))
      spec-info)))

(s/defn to-halite-spec+
  "Allows extra fields, e.g. that ssa adds into spec-infos"
  [senv :- (s/protocol SpecEnv)
   spec-info :- s/Any]
  (when spec-info
    (if (seq (:spec-vars spec-info))
      (update spec-info :spec-vars update-vals (partial halite-type-from-var-type-if-needed senv))
      spec-info)))

(s/defn type-env-from-spec :- (s/protocol TypeEnv)
  "Return a type environment where spec lookups are delegated to tenv, but the in-scope symbols
  are the variables of the given resource spec."
  [senv :- (s/protocol SpecEnv), spec
   ;; TODO: turn this schema check back on
   ;; :- SpecInfo
   ]
  (-> spec
      :spec-vars
      (update-keys symbol)
      (update-vals (partial halite-type-from-var-type-if-needed senv))
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
  [spec-info :- HaliteSpecInfo, inst]
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
  {types/NamespacedKeyword UserSpecInfo})

(s/defschema HaliteSpecMap
  {types/NamespacedKeyword HaliteSpecInfo})

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
  [{:keys [spec-vars refines-to constraints] :as spec-info} :- HaliteSpecInfo]
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

(s/defn build-spec-map :- HaliteSpecMap
  "Return a map of spec-id to SpecInfo, for all specs in senv reachable from the identified root spec."
  [senv :- (s/protocol SpecEnv), root-spec-id :- types/NamespacedKeyword]
  (loop [spec-map {}
         next-spec-ids [root-spec-id]]
    (if-let [[spec-id & next-spec-ids] next-spec-ids]
      (if (contains? spec-map spec-id)
        (recur spec-map next-spec-ids)
        (let [spec-info (halite-lookup-spec senv spec-id)]
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

(s/defn to-halite-spec-env :- (s/protocol SpecEnv)
  "If the spec env is a map object, then update the values to use halite types. If it is not a map
  then rely on the lookup function to perform the conversion."
  [senv :- (s/protocol SpecEnv)]
  (if (map? senv)
    (update-vals senv (partial to-halite-spec senv))
    (reify SpecEnv
      (lookup-spec* [_ spec-id]
        (to-halite-spec senv (lookup-spec* senv spec-id))))))

(defn init
  "Here to load the clojure map protocol extension above"
  [])
