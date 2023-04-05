;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-strip
  "Remove the 'internal' bom fields that are added to a bom so it is can be presented back to the 'user'."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(defn- remove-value-bom [bom]
  (if (= (:$value? bom) {:$primitive-type :Boolean})
    (dissoc bom :$value?)
    bom))

(bom-op/def-bom-multimethod strip-op
  [bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    #{}
    []
    bom/InstanceValue
    bom/ContradictionBom
    bom/NoValueBom
    bom/YesValueBom}
  bom

  bom/PrimitiveBom
  (remove-value-bom bom)

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (-> bom
      (merge (-> bom
                 bom/to-bare-instance-bom
                 (update-vals strip-op)))
      (assoc :$refinements (some-> bom
                                   :$refinements
                                   (update-vals strip-op)))
      (assoc :$concrete-choices (some-> bom
                                        :$concrete-choices
                                        (update-vals strip-op)))
      (dissoc :$constraints)
      base/no-nil-entries
      remove-value-bom))
