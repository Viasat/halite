;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-conjoin-spec-bom
  "Combine a bom with the bom implied by the spec itself."
  (:require [clojure.pprint :as pprint]
            [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod conjoin-spec-bom-op*
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
    bom/ExpressionBom}
  bom

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom
    bom/InstanceLiteralBom}
  (let [spec-id (bom/get-spec-id bom)
        spec (envs/lookup-spec spec-env spec-id)]
    (-> bom
        (merge (-> (if (or (bom/is-concrete-instance-bom? bom)
                           (bom/is-instance-literal-bom? bom))
                     (->> bom
                          bom/to-bare-instance-bom
                          (merge-with bom-analysis/conjoin-boms (->> (bom-analysis/bom-for-spec bom spec-id spec)
                                                                     bom/to-bare-instance-bom)))
                     (->> bom
                          bom/to-bare-instance-bom))
                   (update-vals (partial conjoin-spec-bom-op* spec-env))))
        (assoc :$refinements (some-> bom :$refinements (update-vals (partial conjoin-spec-bom-op* spec-env))))
        (assoc :$concrete-choices (some-> bom :$concrete-choices (update-vals (partial conjoin-spec-bom-op* spec-env))))
        base/no-nil-entries)))

(def trace false)

(s/defn conjoin-spec-bom-op :- bom/Bom
  [spec-env
   bom :- bom/Bom]
  (let [result (conjoin-spec-bom-op* spec-env bom)]
    (when trace
      (pprint/pprint [:conjoin-spec-bom-op bom :result result]))
    result))
