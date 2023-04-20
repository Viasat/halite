;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-canon
  "Convert a bom to a canonical form."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.bom-user :as bom-user]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod canon-op*
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

  bom/PrimitiveBom
  (->> bom
       bom-analysis/detect-empty-enum
       bom-analysis/collapse-to-no-value
       bom-analysis/collapse-ranges-and-enum
       bom-analysis/collapse-enum-into-value
       bom-analysis/collapse-ranges-into-value)

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom
    bom/InstanceLiteralBom}
  (let [bom (-> bom
                bom-analysis/collapse-to-no-value
                bom-analysis/detect-empty-enum
                bom-analysis/detect-empty-concrete-choices)]
    (if (or (bom/is-no-value-bom? bom)
            (bom/is-contradiction-bom? bom))
      bom
      (let [bare-result (-> bom
                            bom/to-bare-instance-bom
                            (update-vals canon-op*))]
        (if (some bom/is-contradiction-bom? (vals bare-result))
          bom/contradiction-bom
          (-> (merge bom bare-result)
              (assoc :$refinements (some-> bom :$refinements (update-vals canon-op*)))
              (assoc :$concrete-choices (some-> bom :$concrete-choices (update-vals canon-op*)))
              base/no-nil-entries))))))

(s/defn canon-op :- bom-user/UserBom
  [bom :- bom-user/UserBom]
  (canon-op* bom))
