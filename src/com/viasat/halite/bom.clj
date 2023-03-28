;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom
  (:require [clojure.string :as string]
            [com.viasat.halite.base :as base]
            [schema.core :as s])
  (:import [clojure.lang Atom]
           [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(defmacro schema-conjunct
  [& pred-names]
  (if (<= (count pred-names) 3)
    (let [[name pred s] pred-names]
      `(s/conditional ~pred ~(or s s/Any) '~name))
    (let [[name pred & rest] pred-names]
      `(s/conditional ~pred (schema-conjunct ~@rest) '~name))))

;;

(def reserved-char \$)

(def dotted-name #"[a-z][a-zA-Z0-9_]*([.][a-zA-Z][a-zA-Z0-9_]*)*")  ;; in particular cannot have a '/' because that is used as a separator

(def workspace-name-length-limit 120)

(def specid-regex-pattern "[A-Z][a-zA-Z0-9_]*[$]v[0-9]+")

(def spec-name-length-limit 80)

(def spec-id-length-limit (+ spec-name-length-limit workspace-name-length-limit 20))

(def WorkspaceName (schema-conjunct keyword keyword?
                                    has-namespace #(nil? (namespace %))
                                    valid-characters #(re-matches dotted-name (name %))
                                    valid-length #(<= (count (name %)) workspace-name-length-limit)))

(defmacro spec-id-schema [regex-pattern length-limit]
  `(schema-conjunct keyword keyword?
                    valid-workspace #(nil? (s/check WorkspaceName (keyword (namespace %))))
                    valid-characters #(re-matches (re-pattern ~regex-pattern) (name %))
                    valid-length #(<= (count (name %)) ~length-limit)))

(def SpecId (spec-id-schema specid-regex-pattern spec-id-length-limit))

;; types for instance expressions

(def VariableKeyword (schema-conjunct keyword keyword?
                                      not-reserved #(not= reserved-char (first (name %)))))

(def InstanceKeyword (schema-conjunct keyword keyword?
                                      not-reserved #(or (not= reserved-char (first (str %)))
                                                        (#{} %))))

(def BareInstance {VariableKeyword s/Any})

;;;;

(defn is-instance-value?
  [x]
  (and (map? x)
       (:$type x)))

(defn is-valid-bom-instance-type-field? [x]
  (let [instance-of (:$instance-of x)
        refines-to (:$refines-to x)]
    (and (or instance-of refines-to)
         (not (and instance-of refines-to)))))

(defn is-abstract-instance-bom?
  [x]
  (and (map? x)
       (:$refines-to x)))

(defn is-concrete-instance-bom?
  [x]
  (and (map? x)
       (:$instance-of x)))

(defn is-instance-bom?
  [x]
  (or (is-abstract-instance-bom? x)
      (is-concrete-instance-bom? x)))

(defn is-primitive-bom?
  [x]
  (and (map? x)
       (not (is-instance-bom? x))
       (not (is-instance-value? x))))

;;;;

(declare InstanceValue)

(def BomValue (s/conditional
               base/integer-or-long? Number
               string? String
               boolean? Boolean
               base/fixed-decimal? FixedDecimal
               is-instance-value? (s/recursive #'InstanceValue)))

(defn is-bom-value? [x]
  (nil? (s/check BomValue x)))

(def InstanceValue
  {:$type SpecId
   VariableKeyword BomValue})

(declare AbstractInstanceBom)

(declare ConcreteInstanceBom)

(def BooleanBom (s/conditional
                 map? {(s/optional-key :$atom) Atom}
                 :else Boolean))

(def IntegerRangeConstraint [(s/one Number :start) (s/one Number :end)])

(def FixedDecimalRangeConstraint [(s/one FixedDecimal :start) (s/one FixedDecimal :end)])

(def RangeConstraint (s/conditional
                      #(base/integer-or-long? (first %)) IntegerRangeConstraint
                      :else FixedDecimalRangeConstraint))

(def RangesConstraint #{RangeConstraint})

(def PrimitiveBom
  {(s/optional-key :$ranges) RangesConstraint
   (s/optional-key :$enum) #{BomValue}
   (s/optional-key :$value?) BooleanBom})

(declare AbstractInstanceBom)

(declare ConcreteInstanceBom)

(def Bom (s/conditional
          is-abstract-instance-bom? (s/recursive #'AbstractInstanceBom)
          is-concrete-instance-bom? (s/recursive #'ConcreteInstanceBom)
          is-primitive-bom? PrimitiveBom
          :else BomValue))

(def VariableValueBom (s/conditional
                       is-instance-value? InstanceValue
                       :else Bom))

(def NoValueBom {:$value? (s/eq false)})

(s/defn is-no-value-bom? [bom]
  (and (map? bom)
       (= 1 (count bom))
       (= false (:$value? bom))))

(def BareInstanceBom {VariableKeyword VariableValueBom})

(def ConcreteInstanceBom
  (assoc BareInstanceBom
         :$instance-of SpecId
         (s/optional-key :$enum) #{InstanceValue}
         (s/optional-key :$value?) BooleanBom
         (s/optional-key :$refinements) {SpecId (s/recursive #'ConcreteInstanceBom)}))

(def AbstractInstanceBom
  (-> ConcreteInstanceBom
      (dissoc :$instance-of)
      (assoc :$refines-to SpecId
             (s/optional-key :$concrete-choices) {SpecId ConcreteInstanceBom})))

(def InstanceBom (s/conditional
                  is-abstract-instance-bom? AbstractInstanceBom
                  is-concrete-instance-bom? ConcreteInstanceBom))

(def InstanceBomOrValue (s/conditional
                         is-abstract-instance-bom? AbstractInstanceBom
                         is-concrete-instance-bom? ConcreteInstanceBom
                         :else InstanceValue))

;;

(def TypeField (s/enum :$refines-to :$instance-of))

(def meta-fields #{:$refines-to
                   :$instance-of
                   :$enum
                   :$value?
                   :$refinements
                   :$concrete-choices
                   :$type})

(s/defn is-instance? :- Boolean
  [instance]
  (boolean (nil? (s/check InstanceBom instance))))

(s/defn to-bare-instance :- BareInstance
  "Take all meta-fields out of instance."
  [instance :- InstanceBom]
  (apply dissoc instance meta-fields))

(s/defn to-bare-instance-bom :- BareInstanceBom
  "Take all meta-fields out of instance."
  [instance :- InstanceBomOrValue]
  (apply dissoc instance meta-fields))
