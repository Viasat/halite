;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-refinements
  "Populate the refinement expressions"
  (:require [clojure.pprint :as pprint]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.spec :as spec]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod refinements-op*
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
        spec-info (envs/lookup-spec spec-env spec-id)]
    (-> bom
        (assoc :$refinements (->> bom
                                  :$refinements
                                  (map (fn [[refinement-name refinement-bom]]
                                         (let [r-spec-id (bom/get-spec-id refinement-bom)
                                               r-spec-info (envs/lookup-spec spec-env r-spec-id)
                                               r-expr (get-in spec-info [:refines-to r-spec-id :expr])
                                               r-field-names (spec/get-field-names r-spec-info)]
                                           [refinement-name (refinements-op*
                                                             spec-env
                                                             (merge-with merge
                                                                         refinement-bom
                                                                         (->> r-field-names
                                                                              (map (fn [field-name]
                                                                                     [field-name {:$expr (list 'get r-expr field-name)}]))
                                                                              (into {}))))])))
                                  (into {})
                                  base/no-empty))
        base/no-nil-entries)))

(def trace false)

(s/defn refinements-op :- bom/Bom
  [spec-env
   bom :- bom/Bom]
  (let [result (refinements-op* spec-env bom)]
    (when trace
      (pprint/pprint [:refinements-op bom :result result]))
    result))
