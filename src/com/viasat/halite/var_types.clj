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

(s/defschema VarTypeAtom
  "Type atoms are always unqualified keywords."
  (apply s/enum primitive-types))

(s/defschema VarInnerType
  (s/conditional
   #(and (vector? %) (= :Decimal (first %))) types/Decimal
   vector? [(s/one (s/enum :Set :Vec) "coll-kind")
            (s/one (s/recursive #'VarInnerType) "elem-type")]
   #(and (keyword? %) (types/bare? %)) VarTypeAtom
   :else types/NamespacedKeyword))

(s/defschema VarType
  (s/conditional
   #(and (vector? %) (= :Maybe (first %))) [(s/one (s/eq :Maybe) "maybe-keyword")
                                            (s/one VarInnerType "inner-type")]
   :else VarInnerType))

;;

(defn maybe-type? [t]
  (and (vector? t)
       (= :Maybe (first t))))

(defn no-maybe [t]
  (if (maybe-type? t)
    (second t)
    t))

(s/defn optional-var-type? :- s/Bool
  [var-type :- VarType]
  (and (vector? var-type) (= :Maybe (first var-type))))

(s/defschema UserSpecVars {types/BareKeyword VarType})

(s/defschema UserSpecInfo
  (assoc envs/SpecInfo
         (s/optional-key :spec-vars) {types/BareKeyword VarType}
         (s/optional-key :constraints) {base/UserConstraintName s/Any}))

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
  (let [check-mandatory #(if (optional-var-type? %)
                           (throw (ex-info "Set and vector types cannot have optional inner types"
                                           {:var-type %}))
                           %)]
    (cond
      (and (keyword? var-type) (types/bare? var-type))
      (cond
        (#{:Integer :String :Boolean} var-type) (keyword var-type)
        :default (throw (ex-info (format "Unrecognized primitive type: %s" var-type) {:var-type var-type})))

      (types/decimal-type? var-type)
      var-type

      (and (optional-var-type? var-type)
           (= 2 (count var-type)))
      [:Maybe (halite-type-from-var-type senv (second var-type))]

      (and (vector? var-type)
           (= 2 (count var-type))
           (#{:Vec :Set} (first var-type))) (let [[coll-type inner-type] var-type]
                                              [coll-type (halite-type-from-var-type senv (check-mandatory inner-type))])

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
    (if (seq (:spec-vars spec-info))
      (update spec-info :spec-vars update-vals (partial halite-type-from-var-type senv))
      spec-info)))

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
