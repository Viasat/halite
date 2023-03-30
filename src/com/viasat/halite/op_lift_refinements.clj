;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-lift-refinements
  (:require [com.viasat.halite.bom :as bom]
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
    #{}
    []
    bom/InstanceValue
    bom/PrimitiveBom}
  bom

  bom/ConcreteInstanceBom
  (let [bom (let [{refinements :$refinements} bom]
              (if (zero? (count refinements))
                (dissoc bom :$refinements)
                (let [refinements-from-children (->> refinements
                                                     (map (fn [[to-spec-id refinement-bom]]
                                                            (let [lifted-child (lift-refinements-op refinement-bom)]
                                                              (:$refinements lifted-child))))
                                                     (reduce (fn [refinements child-refinements]
                                                               (merge-with (fn [r1 r2]
                                                                             ;; TODO: merge the two results here properly
                                                                             (merge r2 r1))
                                                                           refinements
                                                                           child-refinements))
                                                             {}))]
                  (assoc bom :$refinements (merge-with (fn [r1 r2]
                                                         ;; TODO: merge the two results here properly
                                                         (merge r2 r1))
                                                       (-> refinements
                                                           (update-vals #(dissoc % :$refinements)))
                                                       refinements-from-children)))))]

    ;; process child boms
    (merge bom (-> bom
                   bom/to-bare-instance
                   (update-vals lift-refinements-op))))

  bom/AbstractInstanceBom
  bom)
