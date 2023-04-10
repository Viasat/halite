;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-add-constraints
  "Add constraint expressions from specs to boms"
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod add-constraints-op
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
    bom/InstanceValue
    bom/ExpressionBom}
  bom

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom
    bom/InstanceLiteralBom}
  (let [bom (if (or (bom/is-concrete-instance-bom? bom)
                    (bom/is-instance-literal-bom? bom))
              (let [spec-id (bom/get-spec-id bom)
                    {:keys [constraints]} (envs/lookup-spec spec-env spec-id)]
                (when (contains? bom :$constraints)
                  (throw (ex-info "did not expect constraints" {:bom bom})))
                (-> bom
                    (assoc :$constraints (some->> constraints
                                                  (into {})))
                    base/no-nil-entries))
              bom)]
    (-> bom
        (merge (-> bom
                   bom/to-bare-instance-bom
                   (update-vals (partial add-constraints-op spec-env))))
        (assoc :$refinements (some-> bom :$refinements (update-vals (partial add-constraints-op spec-env))))
        (assoc :$concrete-choices (some-> bom :$concrete-choices (update-vals (partial add-constraints-op spec-env))))
        base/no-nil-entries)))
