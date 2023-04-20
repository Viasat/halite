;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.op-strip
  "Remove the 'internal' bom fields that are added to a bom so it is can be presented back to the 'user'."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom :as bom]
            [com.viasat.halite.bom-op :as bom-op]
            [com.viasat.halite.bom-user :as bom-user]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(defn- remove-value-bom [bom]
  (if (or (= (:$value? bom) {:$primitive-type :Boolean})
          (= (:$value? bom) {:$enum #{true false}}))
    (dissoc bom :$value?)
    bom))

(defn- remove-primitive-type [bom]
  (-> bom
      (dissoc :$primitive-type)
      base/no-empty))

(defn- remove-boolean-wildcard [bom]
  (if (and (bom/is-primitive-bom? bom)
           (= (:$enum bom) #{true false}))
    (-> bom
        (dissoc :$enum)
        base/no-empty)
    bom))

(bom-op/def-bom-multimethod strip-op*
  [bom]
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

  #{bom/PrimitiveBom
    bom/ExpressionBom}
  (->> bom remove-value-bom remove-primitive-type remove-boolean-wildcard)

  #{bom/ConcreteInstanceBom
    bom/AbstractInstanceBom
    bom/InstanceLiteralBom}
  (-> bom
      (merge (-> bom
                 bom/to-bare-instance-bom
                 (update-vals strip-op*)))
      (assoc :$refinements (some-> bom
                                   :$refinements
                                   (update-vals strip-op*)))
      (assoc :$concrete-choices (some-> bom
                                        :$concrete-choices
                                        (update-vals strip-op*)))
      (dissoc :$constraints)
      base/no-nil-entries
      remove-value-bom))

(s/defn strip-op :- bom-user/UserBom
  [bom :- bom/Bom]
  (strip-op* bom))
