;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-push-down-to-concrete
  "Update abstract instance bom elements to push their constraints down into the concrete choices."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [com.viasat.halite.bom-op :as bom-op]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod push-down-to-concrete-op
  [bom]

  #{Integer
    FixedDecimal
    String
    Boolean
    bom/NoValueBom
    bom/YesValueBom
    bom/ContradictionBom
    #{}
    []
    bom/InstanceValue
    bom/PrimitiveBom
    bom/ExpressionBom}
  bom

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom
    bom/InstanceLiteralBom}
  (let [{spec-id :$refines-to} bom]
    (let [bom (if (bom/is-abstract-instance-bom? bom)
                (-> bom
                    (assoc :$concrete-choices (-> bom
                                                  :$concrete-choices
                                                  (update-vals (fn [concrete-bom]
                                                                 (-> concrete-bom
                                                                     (assoc :$refinements (merge-with bom-analysis/conjoin-boms
                                                                                                      (:$refinements concrete-bom)
                                                                                                      {spec-id (-> bom
                                                                                                                   (dissoc :$refines-to :$refinements)
                                                                                                                   (assoc :$instance-of spec-id)
                                                                                                                   (dissoc :$concrete-choices))}
                                                                                                      (:$refinements bom))))))))
                    (dissoc :$refinements)
                    bom/strip-bare-fields)
                bom)]
      (if (= 1 (count (:$concrete-choices bom)))
        (->> bom
             :$concrete-choices
             first
             val
             push-down-to-concrete-op)
        (-> bom
            (merge (->> (-> bom
                            bom/to-bare-instance-bom
                            (update-vals push-down-to-concrete-op))
                        (into {})))
            (assoc :$refinements (some-> bom :$refinements (update-vals push-down-to-concrete-op)))
            (assoc :$concrete-choices (some-> bom :$concrete-choices (update-vals push-down-to-concrete-op)))
            base/no-nil-entries)))))
