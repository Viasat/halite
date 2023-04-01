;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-mandatory
  "Ensure that all boms for mandatory fields are marked as required. Convert to contradictions as
  appropriate."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.spec :as spec]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod ensure-value
  [bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    #{}
    []
    bom/InstanceValue
    bom/NoValueBom
    bom/ContradictionBom}
  bom

  #{bom/PrimitiveBom
    bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (if (bom/is-a-no-value-bom? bom)
    bom/contradiction-bom
    (assoc bom :$value? true)))

;;

(bom-op/def-bom-multimethod mandatory-op
  [spec-env bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    bom/PrimitiveBom
    bom/NoValueBom
    bom/ContradictionBom
    #{}
    []
    bom/InstanceValue}
  bom

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (let [spec-id (bom/get-spec-id bom)
        spec (envs/lookup-spec spec-env spec-id)
        spec-mandatory-field-names (->> spec spec/get-mandatory-field-names set)]
    (-> bom
        (merge (->> (-> bom
                        bom/to-bare-instance-bom
                        (update-vals (partial mandatory-op spec-env)))
                    (map (fn [[field-name field-bom]]
                           [field-name (if (spec-mandatory-field-names field-name)
                                         (ensure-value field-bom)
                                         field-bom)]))
                    (into {})))
        (assoc :$refinements (some-> bom :$refinements (update-vals (partial mandatory-op spec-env))))
        (assoc :$concrete-choices (some-> bom :$concrete-choices (update-vals (partial mandatory-op spec-env))))
        base/no-nil-entries)))
