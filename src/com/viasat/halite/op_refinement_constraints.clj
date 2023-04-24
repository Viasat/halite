;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-refinement-constraints
  "Produce constraints from refinements"
  (:require [clojure.pprint :as pprint]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.spec :as spec]
            [com.viasat.halite.envs :as envs]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(declare gather-refinement-constraints)

(defn- gather-refinement-constraints [spec-env expr-context-f bom]
  (let [spec-id (bom/get-spec-id bom)
        spec-info (envs/lookup-spec spec-env spec-id)]
    (some->> bom
             :$refinements
             (mapcat (fn [[r-spec-id sub-bom]]
                       (let [r-spec-info (envs/lookup-spec spec-env r-spec-id)
                             r-expr (get-in spec-info [:refines-to r-spec-id :expr])
                             r-constraints (:constraints r-spec-info)
                             r-field-names (spec/get-field-names r-spec-info)
                             r-bindings (->> r-field-names
                                             (mapcat (fn [field-name]
                                                       [(symbol field-name)
                                                        (list 'get r-expr field-name)]))
                                             vec)
                             expr-context-f' (fn [e]
                                               (expr-context-f
                                                (list 'let
                                                      r-bindings
                                                      e)))]
                         (into (->> r-constraints
                                    (mapv (fn [[r-constraint-name r-constraint-expr]]
                                            [(str "r/" (namespace r-spec-id) "/" (name r-spec-id) "/" r-constraint-name)
                                             (expr-context-f (list 'let ['x0 r-expr] true))])))
                               (gather-refinement-constraints spec-env expr-context-f' sub-bom))))))))

(bom-op/def-bom-multimethod refinement-constraints-op*
  [spec-env bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    #{}
    []
    bom/InstanceValue
    bom/ContradictionBom
    bom/NoValueBom
    bom/YesValueBom
    bom/ExpressionBom
    bom/AbstractInstanceBom
    bom/InstanceLiteralBom
    bom/PrimitiveBom}
  bom

  #{bom/ConcreteInstanceBom}
  (let [spec-id (bom/get-spec-id bom)
        spec-info (envs/lookup-spec spec-env spec-id)
        gathered-constraints (->> bom
                                  (gather-refinement-constraints spec-env identity)
                                  (into {})
                                  (base/no-empty))]
    (-> bom
        (assoc :$constraints
               (merge (:$constraints bom)
                      gathered-constraints))
        base/no-nil-entries)))

(def trace false)

(s/defn refinement-constraints-op :- bom/Bom
  [spec-env
   bom :- bom/Bom]
  (let [result (refinement-constraints-op* spec-env bom)]
    (when trace
      (pprint/pprint [:refinement-constraints-op bom :result result]))
    result))
