;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom-user
  "User facing schema definitions for the Bom structure that describes instance values."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.types :as types]
            [schema.core :as s])
  (:import [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

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

(def specid-regex-str "[A-Z][a-zA-Z0-9_]*[$]v[0-9]+")

(def spec-name-length-limit 80)

(def spec-id-length-limit (+ spec-name-length-limit workspace-name-length-limit 20))

;;

(def WorkspaceName
  "This constrains the namespace that is used in spec identifier keywords."
  (schema-conjunct keyword keyword?
                   has-namespace #(nil? (namespace %))
                   valid-characters #(re-matches dotted-name (name %))
                   valid-length #(<= (count (name %)) workspace-name-length-limit)))

(def SpecId
  "Specs are identified with a keyword."
  (schema-conjunct keyword keyword?
                   valid-workspace #(nil? (s/check WorkspaceName (keyword (namespace %))))
                   valid-characters #(re-matches (re-pattern specid-regex-str) (name %))
                   valid-length #(<= (count (name %)) spec-id-length-limit)))

;; types for instance expressions

(def VariableKeyword
  "The fields, i.e. variables in an instance are identified by non-namespace qualified keywords"
  (schema-conjunct bare-keyword types/bare-keyword?
                   not-reserved #(not= reserved-char (first (name %)))))

(declare InstanceValue)

(defn is-instance-value?
  "See InstanceValue"
  [x]
  (and (map? x)
       (:$type x)))

(defn is-abstract-instance-bom?
  "See AbstractInstanceBom"
  [x]
  (and (map? x)
       (:$refines-to x)))

(defn is-concrete-instance-bom?
  "See ConcreteInstanceBom"
  [x]
  (and (map? x)
       (:$instance-of x)))

(defn is-user-instance-bom?
  "Is this a bom that is used to identify a spec instance."
  [x]
  (or (is-abstract-instance-bom? x)
      (is-concrete-instance-bom? x)))

(defn is-user-primitive-bom?
  "See PrimitiveBom"
  [x]
  (and (map? x)
       (not (is-user-instance-bom? x))
       (not (is-instance-value? x))))

(def BomValue
  "This identifies basic values (i.e. primtives and collections) which appear in boms. These are
  simple values which are not bom objects per se, but are used as a degenerate case of boms."
  (s/conditional
   base/integer-or-long? Number
   string? String
   boolean? Boolean
   base/fixed-decimal? FixedDecimal
   is-instance-value? (s/recursive #'InstanceValue)
   set? #{(s/recursive #'BomValue)}
   vector? [(s/recursive #'BomValue)]))

(def BareInstance
  "A 'plain' instance that does not contain anything other than BomValues (i.e. no bom objects per se)."
  {VariableKeyword BomValue})

(def InstanceValue
  "A bom object that identifies an instance of a spec. This differs from a BareInstance in that it can contains bom objects."
  {:$type SpecId
   VariableKeyword BomValue})

(declare AbstractInstanceBom)

(declare ConcreteInstanceBom)

(def IntegerRangeConstraint
  (s/conditional
   #(nil? (first %)) [(s/one (s/maybe Number) :start) (s/one Number :end)]
   :else [(s/one Number :start) (s/one (s/maybe Number) :end)]))

(def FixedDecimalRangeConstraint
  (s/conditional
   #(nil? (first %)) [(s/one (s/maybe FixedDecimal) :start) (s/one FixedDecimal :end)]
   :else [(s/one FixedDecimal :start) (s/one (s/maybe FixedDecimal) :end)]))

(def RangeConstraint
  "Indicates a range of numerical values, where the first value is included and the final value is
  excluded from the range."
  (s/conditional
   #(base/integer-or-long? (first %)) IntegerRangeConstraint
   :else FixedDecimalRangeConstraint))

;; ranges are interpreted to include the lower bound and exclude the upper bound
;; it is not possible to specify an 'open ended' range

(def RangesConstraint
  "A set of ranges."
  #{RangeConstraint})

(def PrimitiveEnum #{BomValue})

(def UserPrimitiveBom
  "A bom object used to describe a field value. When a :$ranges value is provided, then if the field
  has a value the value must be included in one of the ranges. When an :$enum value is provided,
  then if the field has a value it must be included in the enumeration set. If a :$value? field is
  provided then it constrains whether or not the field must or must not have a
  value. The :$primitive-type field is used for internal purposes to track the type of the field
  which this bom object is being used for. Note: this is not to be be used to describe a composite
  field. Instead use an InstanceBom for that, possibly including the :$enum and :$value? fields."
  {(s/optional-key :$ranges) RangesConstraint
   (s/optional-key :$enum) PrimitiveEnum
   (s/optional-key :$value?) Boolean})

(def NoValueBom
  "Schema that represents that no value is to be provided for the field where this appears."
  {:$value? (s/eq false)})

(def no-value-bom
  "The bom object used to represent the absence of a value."
  {:$value? false})

(s/defn is-no-value-bom?
  "See NoValueBom"
  [bom]
  (= no-value-bom bom))

(def ContradictionBom
  "Schema representing the inability to satisfy all of the constraints."
  {:$contradiction? (s/eq true)})

(def contradiction-bom
  "The bom object representing the inability to satisfy all of the constraints."
  {:$contradiction? true})

(s/defn is-contradiction-bom?
  "See ContradictionBom"
  [bom]
  (= contradiction-bom bom))

(declare UserAbstractInstanceBom)

(declare UserConcreteInstanceBom)

(def UserBom
  "A bom object which is a recursive structure that represents requirements for values that populate
  fields in instances."
  (s/conditional
   is-contradiction-bom? ContradictionBom
   is-abstract-instance-bom? (s/recursive #'UserAbstractInstanceBom)
   is-concrete-instance-bom? (s/recursive #'UserConcreteInstanceBom)
   is-user-primitive-bom? UserPrimitiveBom
   :else BomValue))

(def UserVariableValueBom
  "Everything that can appear as the value of an instance field with a bom object"
  (s/conditional
   is-instance-value? InstanceValue
   is-no-value-bom? NoValueBom
   is-contradiction-bom? ContradictionBom
   :else UserBom))

(def UserBareInstanceBom
  "A map that only contains field names mapped to bom values (i.e. all of the bom object fields have
  been removed)."
  {VariableKeyword UserVariableValueBom})

(def InstanceEnum #{InstanceValue})

(def UserConcreteInstanceBom
  "Indicates that a value in a bom must be an instance of a given spec."
  (assoc UserBareInstanceBom
         :$instance-of SpecId
         (s/optional-key :$enum) InstanceEnum
         (s/optional-key :$value?) Boolean
         (s/optional-key :$refinements) {SpecId (s/conditional
                                                 is-concrete-instance-bom? (s/recursive #'UserConcreteInstanceBom)
                                                 is-no-value-bom? NoValueBom
                                                 :else ContradictionBom)}))

(def UserAbstractInstanceBom
  "Indicates that a value in a bom must be an instance that can be refined to the given spec."
  (-> UserBareInstanceBom
      (assoc :$refines-to SpecId
             (s/optional-key :$value?) Boolean
             (s/optional-key :$refinements) {SpecId (s/conditional
                                                     is-concrete-instance-bom? UserConcreteInstanceBom
                                                     is-no-value-bom? NoValueBom
                                                     :else ContradictionBom)}
             (s/optional-key :$concrete-choices) {SpecId (s/conditional
                                                          is-concrete-instance-bom? UserConcreteInstanceBom
                                                          :else InstanceValue)})))

(def UserInstanceBom
  "Umbrella type for all of the valid bom objects that can appear as the value for a spec instance."
  (s/conditional
   is-abstract-instance-bom? UserAbstractInstanceBom
   is-concrete-instance-bom? UserConcreteInstanceBom
   is-contradiction-bom? ContradictionBom))

(def UserInstanceBomOrValue
  "Includes both InstanceBom and InstanceValue"
  (s/conditional
   is-abstract-instance-bom? UserAbstractInstanceBom
   is-concrete-instance-bom? UserConcreteInstanceBom
   is-contradiction-bom? ContradictionBom
   :else InstanceValue))
