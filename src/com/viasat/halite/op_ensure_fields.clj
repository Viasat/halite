;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-ensure-fields
  "Ensure that all fields from the spec have entries in a bom."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.spec :as spec]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod ensure-value-field
  [bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    #{}
    []
    bom/InstanceValue
    bom/NoValueBom
    bom/YesValueBom
    bom/ContradictionBom}
  bom

  #{bom/PrimitiveBom
    bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (cond
    (= true (:$value? bom)) bom
    (= false (:$value? bom)) bom
    (not (contains? bom :$value?)) (assoc bom :$value? {:$primitive-type :Boolean})
    :default (throw (ex-info "unexpected :$value? condition" {:bom bom}))))

;;

(bom-op/def-bom-multimethod ensure-fields-op
  [spec-env bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    bom/PrimitiveBom
    bom/NoValueBom
    bom/YesValueBom
    bom/ContradictionBom
    #{}
    []
    bom/InstanceValue}
  bom

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (let [spec-id (bom/get-spec-id bom)
        spec (envs/lookup-spec spec-env spec-id)
        spec-field-names (->> spec spec/get-field-names)]
    (-> bom
        (merge (->> spec-field-names
                    (map (fn [field-name]
                           (when-not (contains? bom field-name)
                             [field-name (bom-analysis/bom-for-field true {} [field-name (spec/get-field-type spec field-name)])])))
                    (into {})))
        (assoc :$refinements (some-> bom :$refinements (update-vals (partial ensure-fields-op spec-env))))
        (assoc :$concrete-choices (some-> bom :$concrete-choices (update-vals (partial ensure-fields-op spec-env))))
        base/no-nil-entries)))
