;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-inflate
  "Take a flat sequence of bom leaves and merge them into a bom tree."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-analysis :as bom-analysis]
            [com.viasat.halite.bom-op :as bom-op]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(defn- bring-value-field-into-bom [flat-bom-map path bom]
  (let [value-path (conj path :$value?)
        value-entry? (contains? flat-bom-map value-path)
        value-entry (flat-bom-map value-path)]
    (cond
      (and (map? bom)
           value-entry?
           (or (map? value-entry)
               (= true value-entry)))
      (assoc bom :$value? value-entry)

      (and (map? bom)
           value-entry?
           (= false value-entry))
      bom/no-value-bom

      (not value-entry?)
      bom

      :default (throw (ex-info "value-entry scenario not yet supported" {:bom bom
                                                                         :value-entry value-entry})))))

(bom-op/def-bom-multimethod inflate-op*
  [flat-bom-map path bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    #{}
    []
    bom/InstanceValue
    bom/ContradictionBom
    bom/NoValueBom
    bom/YesValueBom}
  bom

  bom/PrimitiveBom
  (bom-analysis/merge-boms bom
                           (->> (flat-bom-map path)
                                (bring-value-field-into-bom flat-bom-map path)))

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (-> (bring-value-field-into-bom flat-bom-map path bom)
      (merge (->> bom
                  bom/to-bare-instance-bom
                  (map (fn [[field-name field-bom]]
                         [field-name (inflate-op* flat-bom-map (conj path field-name) field-bom)]))
                  (into {})))
      (assoc :$refinements (some->> bom
                                    :$refinements
                                    (mapcat (fn [[spec-id sub-bom]]
                                              [spec-id (inflate-op* flat-bom-map
                                                                    (conj path :$refinements spec-id)
                                                                    sub-bom)]))
                                    (into {})))
      (assoc :$concrete-choices (some->> bom
                                         :$concrete-choices
                                         (mapcat (fn [[spec-id sub-bom]]
                                                   [spec-id (inflate-op* flat-bom-map
                                                                         (conj path :$concrete-choices spec-id)
                                                                         sub-bom)]))
                                         (into {})))
      base/no-nil-entries))

(defn inflate-op [bom flat-bom-map]
  (inflate-op* flat-bom-map [] bom))
