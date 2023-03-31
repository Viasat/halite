;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-lift-refinements
  "Transform a bom such that nested $refinements field values are all 'lifted' to the top-level."
  (:require [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [com.viasat.halite.bom-op :as bom-op]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod lift-refinements-op
  [bom]

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

  #{bom/AbstractInstanceBom
    bom/ConcreteInstanceBom}
  (let [bom  (-> (if (bom/is-concrete-instance-bom? bom)
                   (let [{refinements :$refinements} bom]
                     (if (zero? (count refinements))
                       (dissoc bom :$refinements)
                       (let [refinements-from-children (->> refinements
                                                            (map (fn [[to-spec-id refinement-bom]]
                                                                   (let [lifted-child (lift-refinements-op refinement-bom)]
                                                                     (:$refinements lifted-child))))
                                                            (reduce (fn [refinements child-refinements]
                                                                      (merge-with bom-analysis/merge-boms
                                                                                  refinements
                                                                                  child-refinements))
                                                                    {}))]
                         (assoc bom :$refinements (merge-with bom-analysis/merge-boms
                                                              (-> refinements
                                                                  (update-vals #(dissoc % :$refinements)))
                                                              refinements-from-children)))))
                   bom)
                 (assoc :$concrete-choices (some-> bom :$concrete-choices (update-vals lift-refinements-op)))
                 bom-op/no-nil-entries)]

    ;; process child boms
    (merge bom (-> bom
                   bom/to-bare-instance
                   (update-vals lift-refinements-op)))))
