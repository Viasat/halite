;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-find-refinements
  "Extend boms to include the actual refinements in play per the spec-env"
  (:require [clojure.pprint :as pprint]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.spec :as spec]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(defn- extend-refinement-path [bom refinement-path]
  (if (empty? refinement-path)
    bom
    (let [current-bom (-> bom
                          :$refinements
                          (get (:to-spec-id (first refinement-path))))]
      (if (nil? current-bom)
        (let [new-refinement (reduce (fn [bom path-element]
                                       (let [new-node {:$instance-of (:to-spec-id path-element)}]
                                         (if (nil? bom)
                                           new-node
                                           (assoc-in new-node [:$refinements (:$instance-of bom)] bom))))
                                     nil
                                     (reverse refinement-path))]
          (assoc-in bom [:$refinements (:$instance-of new-refinement)] new-refinement))
        (assoc-in bom [:$refinements (:to-spec-id (first refinement-path))]
                  (extend-refinement-path current-bom (rest refinement-path)))))))

(bom-op/def-bom-multimethod find-refinements-op*
  [spec-env bom]

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
    bom/ExpressionBom
    bom/AbstractInstanceBom}
  bom

  #{bom/ConcreteInstanceBom
    bom/InstanceLiteralBom}
  (let [spec-id (bom/get-spec-id bom)
        refinement-paths (spec/find-comprehensive-refinement-paths-from spec-env spec-id)]
    (reduce (fn [bom refinement-path]
              (extend-refinement-path bom refinement-path))
            bom
            refinement-paths)))

(def trace false)

(s/defn find-refinements-op :- bom/Bom
  [spec-env
   bom :- bom/Bom]
  (let [result (find-refinements-op* spec-env bom)]
    (when trace
      (pprint/pprint [:find-refinements-op bom :result result]))
    result))
