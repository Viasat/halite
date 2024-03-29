;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-type-check
  "Check that all of the values provided in a bom match the expected types, per the specs in the
  spec-env."
  (:require [com.viasat.halite.b-err :as b-err]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.bom-user :as bom-user]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.lib.fixed-decimal :as fixed-decimal]
            [com.viasat.halite.lib.format-errors :as format-errors]
            [com.viasat.halite.spec :as spec]
            [com.viasat.halite.type-check :as type-check]
            [com.viasat.halite.types :as types]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(declare get-bom-type)

(bom-op/def-bom-multimethod get-bom-type*
  [bom]

  Integer
  :Integer

  FixedDecimal
  (types/decimal-type (fixed-decimal/get-scale bom))

  String
  :String

  Boolean
  :Boolean

  #{bom/NoValueBom
    bom/YesValueBom
    bom/ContradictionBom}
  nil

  #{bom/ExpressionBom
    bom/InstanceLiteralBom}
  (throw (ex-info "unexpected bom element" {:bom bom}))

  bom/PrimitiveBom
  (let [{ranges :$ranges enum :$enum} bom
        enum-types (->> enum (map get-bom-type*) set)
        ranges-type (some->> ranges first first get-bom-type*)]
    (when (> (count enum-types) 1)
      (throw (ex-info "conflicting types in enum" {:bom bom
                                                   :enum enum})))
    (let [all-types (remove nil? (set [(first enum-types) ranges-type]))]
      (when (> (count all-types) 1)
        (throw (ex-info "conflicting types in enum" {:bom bom
                                                     :enum enum
                                                     :ranges ranges})))
      (first all-types)))

  #{}
  (let [types (->> bom
                   (map get-bom-type)
                   set)]
    (when (> (count types) 1)
      (format-errors/throw-err (b-err/type-mismatch-collection-elements {:set bom})))
    (->> types
         first
         types/set-type))

  []
  (let [types (->> bom
                   (map get-bom-type)
                   distinct
                   vec)]
    (when (> (count types) 1)
      (format-errors/throw-err (b-err/type-mismatch-collection-elements {:vec bom})))
    (->> types
         first
         types/vector-type))

  #{bom/InstanceValue
    bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (bom/instance-bom-halite-type bom))

(s/defn get-bom-type :- (s/maybe types/HaliteType)
  [bom :- bom/VariableValueBom]
  (get-bom-type* bom))

(bom-op/def-bom-multimethod type-check*
  [spec-env bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    bom/PrimitiveBom
    bom/NoValueBom
    bom/YesValueBom
    bom/ContradictionBom
    #{}
    []}
  (get-bom-type bom)

  #{bom/ExpressionBom
    bom/InstanceLiteralBom}
  (throw (ex-info "unexpected bom element" {:bom bom}))

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (let [spec-id (bom/get-spec-id bom)
        spec (envs/lookup-spec spec-env spec-id)
        spec-optional-field-names (->> spec
                                       spec/get-optional-field-names
                                       set)]
    (->> (bom/to-bare-instance-bom bom)
         (map (fn [[field-name field-bom]]
                (when (and (bom-op/bom-assumes-optional? field-bom)
                           (not (spec-optional-field-names field-name)))
                  (throw (ex-info "only optional fields can have a :$value bom field " {:field-name field-name
                                                                                        :field-bom field-bom})))
                (let [field-type (spec/get-field-type spec field-name)
                      field-bom-type (get-bom-type field-bom)]
                  (when (not field-type)
                    (throw (ex-info "field not found: " {:bom bom
                                                         :field-bom field-bom
                                                         :field-name field-name})))
                  (when (and (not= true (:abstract? spec))
                             (bom/is-concrete-instance-bom? bom)
                             (types/spec-type? field-bom-type)
                             (bom/is-concrete-instance-bom? field-bom)
                             (let [field-spec (envs/lookup-spec spec-env (bom/get-spec-id field-bom))]
                               (when (nil? field-spec)
                                 (throw (ex-info "spec not found for field-bom" {:bom bom
                                                                                 :field-bom field-bom
                                                                                 :field-spec-id (bom/get-spec-id field-bom)})))
                               (= true (:abstract? field-spec))))
                    (throw (ex-info "cannot use an abstract instance to construct a concrete instance" {:bom bom
                                                                                                        :field-name field-name
                                                                                                        :field-type field-type
                                                                                                        :field-bom field-bom})))
                  (when (and field-bom-type
                             (not (types/subtype? field-bom-type field-type)))
                    (throw (ex-info "unexpected type: " {:field-type field-type
                                                         :field-bom-type field-bom-type
                                                         :field-bom field-bom}))))))
         dorun)
    (->> (some-> bom :$refinements vals)
         (map (partial type-check* spec-env))
         dorun)
    (->> (some-> bom :$concrete-choices vals)
         (map (partial type-check* spec-env))
         dorun)
    (bom/instance-bom-halite-type bom))

  #{bom/InstanceValue}
  (do
    (type-check/type-check spec-env (envs/type-env {}) bom)
    (bom/instance-bom-halite-type bom)))

(s/defn determine-type :- types/HaliteType
  [spec-env
   bom :- bom-user/UserBom]
  (type-check* spec-env bom))

(s/defn type-check-op :- bom-user/UserBom
  [spec-env
   bom :- bom-user/UserBom]
  (determine-type spec-env bom)
  bom)
