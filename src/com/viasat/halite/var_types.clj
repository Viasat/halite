;; Copyright (c) 2022 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.var-types
  "User interface types"
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.types :as types]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(def primitive-types [:String :Integer :Boolean])

(def primitive-types-set (set primitive-types))

(s/defschema VarTypeAtom
  "Type atoms are always unqualified keywords."
  (apply s/enum primitive-types))

(s/defschema VarInnerType
  (s/conditional
   #(and (vector? %) (= :Decimal (first %))) types/Decimal
   vector? [(s/one (s/enum :Set :Vec) "coll-kind")
            (s/one (s/recursive #'VarInnerType) "elem-type")]
   types/bare-keyword? VarTypeAtom
   :else types/NamespacedKeyword))

(s/defschema VarType
  (s/conditional
   #(and (vector? %) (= :Maybe (first %))) [(s/one (s/eq :Maybe) "maybe-keyword")
                                            (s/one VarInnerType "inner-type")]
   :else VarInnerType))

;;

(defn maybe-type? [t]
  (and (vector? t)
       (= :Maybe (first t))
       (= 2 (count t))))

(defn no-maybe [t]
  (if (maybe-type? t)
    (second t)
    t))

(s/defn maybe-type :- VarType
  "Construct a type representing values that are 'maybe' of the given type."
  [t :- VarType]
  (if (maybe-type? t)
    t
    [:Maybe t]))

(s/defn elem-type :- (s/maybe VarType)
  "Return the type of the element in the collection type given, if it is known.
  Otherwise, or if the given type is not a collection, return nil."
  [t]
  (when (and (vector? t)
             (= 2 (count t)))
    (let [[x y] t]
      (when (or (= :Set x) (= :Vec x))
        y))))

(s/defn change-elem-type :- types/HaliteType
  "Construct a type value that is like coll-type, except it contains new-element-type."
  [coll-type :- VarType
   new-elem-type :- types/HaliteType]
  (assoc coll-type 1 new-elem-type))

;;

(s/defschema UserSpecVars {types/BareKeyword VarType})

(s/defschema UserSpecInfo
  (assoc envs/SpecInfo
         (s/optional-key :fields) {types/BareKeyword VarType}
         (s/optional-key :constraints) #{envs/Constraint}))

(s/defschema UserSpecMap
  {types/NamespacedKeyword UserSpecInfo})

;;

(s/defn lookup-spec-abstract? :- (s/maybe Boolean)
  "Lookup whether the given spec is abstract. Produce nil if the spec does not exist. This function
  avoids enforcing a schema on the spec specifically so that it can be used when the spec
  environment is being converted between formats."
  [senv :- (s/protocol envs/SpecEnv), spec-id :- types/NamespacedKeyword]
  (when-let [spec (envs/lookup-spec* senv spec-id)]
    (boolean (:abstract? spec))))

(s/defn halite-type-from-var-type :- types/HaliteType
  [senv :- (s/protocol envs/SpecEnv)
   var-type :- VarType]
  (let [check-mandatory #(if (maybe-type? %)
                           (throw (ex-info "Set and vector types cannot have optional inner types"
                                           {:var-type %}))
                           %)]
    (cond
      (types/bare-keyword? var-type)
      (cond
        (primitive-types-set var-type) var-type
        :default (throw (ex-info (format "Unrecognized primitive type: %s" var-type) {:var-type var-type})))

      (types/decimal-type? var-type)
      var-type

      (maybe-type? var-type)
      (types/maybe-type (halite-type-from-var-type senv (no-maybe var-type)))

      (elem-type var-type)
      (change-elem-type var-type (halite-type-from-var-type senv (check-mandatory (elem-type var-type))))

      (types/namespaced-keyword? var-type)
      (let [abstract? (lookup-spec-abstract? senv var-type)]
        (if (nil? abstract?)
          (throw (ex-info (format "Spec not found: %s" var-type) {:var-type var-type}))
          (if abstract?
            (types/abstract-spec-type var-type)
            (types/concrete-spec-type var-type))))

      :else (throw (ex-info "Invalid spec variable type" {:var-type var-type})))))

(s/defn to-halite-spec :- (s/maybe envs/SpecInfo)
  "Create specs with halite types from specs with var types"
  [senv :- (s/protocol envs/SpecEnv)
   spec-info :- s/Any]
  (when spec-info
    (let [spec-info (if (seq (:fields spec-info))
                      (update spec-info :fields update-vals (partial halite-type-from-var-type senv))
                      spec-info)]
      (if (:constraints spec-info)
        (update spec-info :constraints #(mapv (fn [{:keys [name expr]}]
                                                [name expr]) %))
        spec-info))))

(s/defn halite-spec-env :- (s/protocol envs/SpecEnv)
  [spec-info-map :- {types/NamespacedKeyword UserSpecInfo}]
  (-> spec-info-map
      (update-vals (partial to-halite-spec spec-info-map))
      envs/->SpecEnvImpl))

(s/defn to-halite-spec-env :- (s/protocol envs/SpecEnv)
  "If the spec env is a map object, then update the values to use halite types. If it is not a map
  then rely on the lookup function to perform the conversion."
  [senv :- (s/protocol envs/SpecEnv)]
  (if (map? senv)
    (update-vals senv (partial to-halite-spec senv))
    (reify envs/SpecEnv
      (lookup-spec* [_ spec-id]
        (to-halite-spec senv (envs/lookup-spec* senv spec-id))))))
