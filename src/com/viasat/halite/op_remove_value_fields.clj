;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-remove-value-fields
  "Remove the extra :$value? boms that were added for propagation"
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [com.viasat.halite.spec :as spec]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod remove-value-from-mandatory
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

  #{bom/BasicBom
    bom/ExpressionBom
    bom/ConcreteInstanceBom
    bom/AbstractInstanceBom
    bom/InstanceLiteralBom}
  (cond
    (= true (:$value? bom)) (dissoc bom :$value?) ;; since the field is mandatory, the :$value? field is not needed
    (= false (:$value? bom)) bom/no-value-bom
    (= (:$value? bom) {:$enum #{true false}}) (dissoc bom :$value?)
    (not (contains? bom :$value?)) bom
    :default (throw (ex-info "unexpected :$value? condition" {:bom bom}))))

;;

(bom-op/def-bom-multimethod remove-value-fields-op*
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
    bom/InstanceValue}
  bom

  #{bom/BasicBom
    bom/ExpressionBom}
  (cond
    (= (:$value? bom) {:$primitive-type :Boolean}) (dissoc bom :$value?)
    (= (:$value? bom) {:$enum #{true false}}) (dissoc bom :$value?)
    :default bom)

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom
    bom/InstanceLiteralBom}
  (let [spec-id (bom/get-spec-id bom)
        spec (envs/lookup-spec spec-env spec-id)
        spec-mandatory-field-names (->> spec spec/get-mandatory-field-names set)]
    (-> bom
        (merge (->> (-> bom
                        bom/to-bare-instance-bom
                        (update-vals (partial remove-value-fields-op* spec-env)))
                    (map (fn [[field-name field-bom]]
                           [field-name (if (spec-mandatory-field-names field-name)
                                         (remove-value-from-mandatory field-bom)
                                         field-bom)]))
                    (into {})))
        (assoc :$refinements (some-> bom :$refinements (update-vals (partial remove-value-fields-op* spec-env))))
        (assoc :$concrete-choices (some-> bom :$concrete-choices (update-vals (partial remove-value-fields-op* spec-env))))
        base/no-nil-entries)))

(defn remove-value-fields-op [spec-env bom]
  (remove-value-fields-op* spec-env bom))
