;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-extract-constraints
  "Lift all constraints up out of the bom to a 'top' level collection"
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.envs :as envs]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod extract-constraints-op*
  [path bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    #{}
    []
    bom/InstanceValue
    bom/PrimitiveBom}
  nil

  #{bom/ContradictionBom
    bom/NoValueBom
    bom/YesValueBom}
  (throw (ex-info "unexpected bom element" {:bom bom}))

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (let [spec-id (bom/get-spec-id bom)]
    (->> (reduce into
                 (or (some->> bom
                              :$constraints
                              (map (fn [[constraint-name constraint-e]]
                                     {:constraint-path (conj path spec-id constraint-name)
                                      :constraint-e constraint-e})))
                     [])
                 [(->> bom
                       bom/to-bare-instance-bom
                       (mapcat (fn [[field-name field-bom]]
                                 (extract-constraints-op* (conj path field-name) field-bom))))
                  (some->> bom
                           :$refinements
                           (mapcat (fn [[spec-id sub-bom]]
                                     (extract-constraints-op* (conj path :$refinements spec-id) sub-bom))))
                  (some->> bom :$concrete-choices
                           (mapcat (fn [[spec-id sub-bom]]
                                     (extract-constraints-op* (conj path :$concrete-choices spec-id) sub-bom))))])
         (remove nil?)
         vec)))

(defn extract-constraints-op [bom]
  (extract-constraints-op* [] bom))

