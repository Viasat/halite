;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-contradictions
  "Bubble contradictions up."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.spec :as spec]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod bubble-up-contradictions
  [bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    #{}
    []
    bom/InstanceValue
    bom/NoValueBom
    bom/ContradictionBom
    bom/PrimitiveBom}
  bom

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (let [;; walk all child boms first to pull up contradictions in them
        bom (-> bom
                (merge (->> (-> bom
                                bom/to-bare-instance-bom
                                (update-vals bubble-up-contradictions))
                            (into {})))
                (assoc :$refinements (some-> bom :$refinements (update-vals bubble-up-contradictions)))
                (assoc :$concrete-choices (some->> (some-> bom :$concrete-choices (update-vals bubble-up-contradictions))
                                                   (remove (fn [[_ bom]]
                                                             (or (bom/is-contradiction-bom? bom)
                                                                 (bom/is-no-value-bom? bom))))
                                                   (into {})))
                base/no-nil-entries)
        ;; detect if any fields are contradictions
        bom (if (->> bom
                     bom/to-bare-instance-bom
                     vals
                     (some #(= bom/contradiction-bom %)))
              (if (= true (:$value? bom))
                bom/contradiction-bom
                bom/no-value-bom)
              bom)

        ;; detect if all concrete choices have been removed
        bom (if (and (bom/is-abstract-instance-bom? bom)
                     (empty? (:$concrete-choices bom)))
              (if (= true (:$value? bom))
                bom/contradiction-bom
                bom/no-value-bom)
              bom)
        ;; detect if any refinement raises a contradictions
        bom (if (nil? (:$refinements bom))
              bom
              (if (->> (:$refinements bom)
                       (some #(= bom/contradiction-bom %)))
                bom/contradiction-bom
                bom))]
    bom))
