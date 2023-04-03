;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-add-types
  "Add type indicators to primitive boms"
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.spec :as spec]
            [com.viasat.halite.types :as types]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod merge-type-op
  [type bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    #{}
    []
    bom/ContradictionBom
    bom/InstanceValue
    bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  bom

  bom/NoValueBom
  bom/no-value-bom

  #{bom/PrimitiveBom
    bom/YesValueBom}
  (assoc bom :$primitive-type type))

(bom-op/def-bom-multimethod add-types-op
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
    []
    bom/InstanceValue}
  bom

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (let [spec-id (bom/get-spec-id bom)
        spec (envs/lookup-spec spec-env spec-id)]
    (-> bom
        (merge (->> bom
                    bom/to-bare-instance-bom
                    (map (fn [[field-name field-bom]]
                           [field-name (merge-type-op (->> (spec/get-field-type spec field-name)
                                                           types/no-maybe)
                                                      field-bom)]))
                    (into {})))
        (assoc :$refinements (some-> bom :$refinements (update-vals (partial add-types-op spec-env))))
        (assoc :$concrete-choices (some-> bom :$concrete-choices (update-vals (partial add-types-op spec-env))))
        base/no-nil-entries)))
