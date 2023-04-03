;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-merge-spec-bom
  "Combine a bom with the bom implied by the spec itself."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod merge-spec-bom-op
  [spec-env bom]

  #{Integer
    FixedDecimal
    String
    Boolean
    bom/NoValueBom
    bom/ContradictionBom
    #{}
    []
    bom/InstanceValue
    bom/PrimitiveBom}
  bom

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (let [spec-id (bom/get-spec-id bom)
        spec (envs/lookup-spec spec-env spec-id)]
    (-> bom
        (merge (-> (if (bom/is-concrete-instance-bom? bom)
                     (->> bom
                          bom/to-bare-instance-bom
                          (merge-with bom-analysis/merge-boms (->> (bom-analysis/bom-for-spec spec-id spec)
                                                                   bom/to-bare-instance-bom)))
                     (->> bom
                          bom/to-bare-instance-bom))
                   (update-vals (partial merge-spec-bom-op spec-env))))
        (assoc :$refinements (some-> bom :$refinements (update-vals (partial merge-spec-bom-op spec-env))))
        (assoc :$concrete-choices (some-> bom :$concrete-choices (update-vals (partial merge-spec-bom-op spec-env))))
        base/no-nil-entries)))
