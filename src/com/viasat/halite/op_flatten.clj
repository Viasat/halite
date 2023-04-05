;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-flatten
  "Flatten the bom tree into a simple sequence of variables. This brings all the leaves up to the 'top'"
  (:require [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(bom-op/def-bom-multimethod flatten-op*
  [path bom]
  #{Integer
    FixedDecimal
    String
    Boolean
    #{}
    []
    bom/InstanceValue}
  nil

  #{bom/ContradictionBom
    bom/NoValueBom
    bom/YesValueBom}
  (throw (ex-info "unexpected bom element" {:bom bom}))

  bom/PrimitiveBom
  (->> [{:path path :value bom}
        (when (:$value? bom)
          {:path (conj path :$value?) :value (:$value? bom)})]
       (remove nil?)
       vec)

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom}
  (->> (reduce into
               [(when (:$value? bom)
                  {:path (conj path :$value?) :value (:$value? bom)})
                (cond
                  (:$valid? bom) {:path (conj path :$valid?) :value (:$valid? bom)}
                  (= true (:$extrinsic? bom)) {:path (conj path :$valid?) :value (:$valid? :unknown)}
                  :default nil)]
               [(->> bom
                     bom/to-bare-instance-bom
                     (mapcat (fn [[field-name field-bom]]
                               (flatten-op* (conj path field-name) field-bom))))
                (some->> bom
                         :$refinements
                         (mapcat (fn [[spec-id sub-bom]]
                                   (flatten-op* (conj path :$refinements spec-id) sub-bom))))
                (some->> bom :$concrete-choices
                         (mapcat (fn [[spec-id sub-bom]]
                                   (flatten-op* (conj path :$concrete-choices spec-id) sub-bom))))])
       (remove nil?)
       vec))

(defn flatten-op [bom]
  (flatten-op* [] bom))
