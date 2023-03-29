;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-type-check
  (:require [clojure.set :as set]
            [com.viasat.halite.b-err :as b-err]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
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

  bom/NoValueBom
  nil

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

(declare type-check)

(bom-op/def-bom-multimethod type-check*
  [spec-env bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    bom/PrimitiveBom
    bom/NoValueBom
    #{}
    []}
  (get-bom-type bom)

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (let [spec-id (bom/get-spec-id bom)
        spec (envs/lookup-spec spec-env spec-id)
        spec-optional-field-names (->> spec
                                       spec/get-optional-field-names
                                       set)]
    (->> (bom/to-bare-instance bom)
         (map (fn [[field-name field-bom]]
                (when (and (bom-op/bom-assumes-optional? field-bom)
                           (not (spec-optional-field-names field-name)))
                  (throw (ex-info "only optional fields can have a :$value bom field " {:field-name field-name
                                                                                        :field-bom field-bom})))
                (let [field-type (spec/get-field-type spec field-name)
                      field-bom-type (get-bom-type field-bom)]
                  (when (and field-bom-type
                             (not= field-type field-bom-type))
                    (throw (ex-info "unexpected type: " {:field-type field-type
                                                         :field-bom-type field-bom-type
                                                         :field-bom field-bom}))))))
         dorun)
    (bom/instance-bom-halite-type bom))

  #{bom/InstanceValue}
  (do
    (type-check/type-check spec-env (envs/type-env {}) bom)
    (bom/instance-bom-halite-type bom)))

(s/defn determine-type :- types/HaliteType
  [spec-env
   bom :- bom/VariableValueBom]
  (type-check* spec-env bom))

(s/defn type-check-op :- bom/VariableValueBom
  [spec-env
   bom :- bom/VariableValueBom]
  (determine-type spec-env bom)
  bom)
