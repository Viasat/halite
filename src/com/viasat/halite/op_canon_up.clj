;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-canon-up
  "Convert a bom to a canonical form on the output path. Assumes that un-needed :$value? fields have
  been removed."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [com.viasat.halite.bom-op :as bom-op]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod canon-up-op
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
    bom/ExpressionBom}
  bom

  bom/BasicBom
  (if (and (or (not (contains? bom :$value?))
               (= true (:$value? bom)))
           (= 1 (count (:$enum bom))))
    (first (:$enum bom))
    bom)

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom
    bom/InstanceLiteralBom}
  (let [bare-result (-> bom
                        bom/to-bare-instance-bom
                        (update-vals canon-up-op))]
    (if (some bom/is-contradiction-bom? (vals bare-result))
      bom/contradiction-bom
      (-> (merge bom bare-result)
          (assoc :$refinements (some-> bom :$refinements (update-vals canon-up-op)))
          (assoc :$concrete-choices (some-> bom :$concrete-choices (update-vals canon-up-op)))
          base/no-nil-entries))))
