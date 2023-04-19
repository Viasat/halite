;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.types :as types]
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

;;;;

(defn is-instance-value?
  "See InstanceValue"
  [x]
  (and (map? x)
       (:$type x)))

;;

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

(defn is-instance-literal-bom?
  "See InstanceLiteralBom"
  [x]
  (and (map? x)
       (:$instance-literal-type x)))

(defn is-instance-bom?
  "Is this a bom that is used to identify a spec instance."
  [x]
  (or (is-abstract-instance-bom? x)
      (is-concrete-instance-bom? x)
      (is-instance-literal-bom? x)))

;;

(defn is-expression-bom?
  "See ExpressionBom"
  [x]
  (and (map? x)
       (contains? x :$expr)))

;;

(defn is-primitive-bom?
  "See PrimitiveBom"
  [x]
  (and (map? x)
       (not (is-instance-bom? x))
       (not (is-instance-value? x))
       (not (is-expression-bom? x))))

;;;;

;; Boms describe values. The following bom fields are used:
;; :$enum : if a value is provided, it must be a value that is in the set
;; :$ranges : if a value is provided it must be within one of the ranges
;; :$value? : 'true' means a value must be provided, 'false' means a value must not be provided
;; :$instance-of : the value must be an instance of this spec type
;; :$refines-to : the value provided must refine to this spec type
;; :$refinements : the value must satisfy all of the specified refinements, a "no value" in the refinement identifies a disallowed refinement
;; :$concrete-choices : the value must satisfy one of the values in the map, an empty map is equivalent to "no value"

;; :$valid? : indicates whether an instance in a refinement chain must be present
;; :$extrinsic? : indicates if a leg of a refinement chain is reached via an extrinsic refinement
;; :$valid-var-path : the path to the variable where the constraints for this instance are to be accumulated
;; :$valid-vars : map of the vars representing 'valid' invocations in expressions for this bom

;; Special bom values:
;; no-value-bom : indicates that no value is to be provided in this location (this is roughly like a 'nil' value)
;; contradiction-bom : indicates that it is impossible to satisfy all of the constraints indicated in the bom for this value (this is roughly like an exception)
;; NOTE: these two mean different things

(declare InstanceValue)

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

(defn is-bom-value?
  "See BomValue"
  [x]
  (nil? (s/check BomValue x)))

(def BareInstance
  "A 'plain' instance that does not contain anything other than BomValues (i.e. no bom objects per se)."
  {VariableKeyword BomValue})

(def InstanceValue
  "A bom object that identifies an instance of a spec. This differs from a BareInstance in that it can contains bom objects."
  {:$type SpecId
   VariableKeyword BomValue})

(declare AbstractInstanceBom)

(declare ConcreteInstanceBom)

(def BooleanBom
  "A bom object used to describe a boolean field."
  (s/conditional
   map? {(s/optional-key :$primitive-type) types/HaliteType
         (s/optional-key :$enum) #{Boolean}}
   :else Boolean))

(def IntegerRangeConstraint
  [(s/one Number :start) (s/one Number :end)])

(def FixedDecimalRangeConstraint
  [(s/one FixedDecimal :start) (s/one FixedDecimal :end)])

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

(def PrimitiveBom
  "A bom object used to describe a field value. When a :$ranges value is provided, then if the field
  has a value the value must be included in one of the ranges. When an :$enum value is provided,
  then if the field has a value it must be included in the enumeration set. If a :$value? field is
  provided then it constrains whether or not the field must or must not have a
  value. The :$primitive-type field is used for internal purposes to track the type of the field
  which this bom object is being used for. Note: this is not to be be used to describe a composite
  field. Instead use an InstanceBom for that, possibly including the :$enum and :$value? fields."
  {(s/optional-key :$ranges) RangesConstraint
   (s/optional-key :$enum) #{BomValue}
   (s/optional-key :$value?) BooleanBom
   (s/optional-key :$primitive-type) types/HaliteType})

(declare AbstractInstanceBom)

(declare ConcreteInstanceBom)

(declare InstanceLiteralBom)

(def ExpressionBom
  "Used internally to assign expressions to fields, e.g. for instance literals."
  {:$expr s/Any})

(def Bom
  "A bom object which is a recursive structure that represents requirements for values that populate
  fields in instances."
  (s/conditional
   is-abstract-instance-bom? (s/recursive #'AbstractInstanceBom)
   is-concrete-instance-bom? (s/recursive #'ConcreteInstanceBom)
   is-instance-literal-bom? (s/recursive #'InstanceLiteralBom)
   is-primitive-bom? PrimitiveBom
   is-expression-bom? ExpressionBom
   :else BomValue))

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

(s/defn is-a-no-value-bom?
  "This handles concrete instance boms which also have a field indicating whether they represent a
  value."
  [bom]
  (= false (:$value? bom)))

(def YesValueBom
  "Schema for a bom object that represents the mandatory presence of a field."
  {:$value? (s/eq true)})

(def yes-value-bom
  "The bom object used to represent the mandatory presence of a field."
  {:$value? true})

(s/defn is-yes-value-bom?
  "See YesValueBom"
  [bom]
  (= yes-value-bom bom))

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

(def VariableValueBom
  "Everything that can appear as the value of an instance field with a bom object"
  (s/conditional
   is-instance-value? InstanceValue
   is-no-value-bom? NoValueBom
   is-contradiction-bom? ContradictionBom
   :else Bom))

(def BareInstanceBom
  "A map that only contains field names mapped to bom values (i.e. all of the bom object fields have
  been removed)."
  {VariableKeyword VariableValueBom})

(def InstanceLiteralBom
  "An internal representation used to brin instance literals from spec expression into the bom directly as
  objects (i.e. outside of their containing expressions)."
  {VariableKeyword (s/conditional
                    is-expression-bom? ExpressionBom
                    :else BomValue)
   :$instance-literal-type SpecId
   (s/optional-key :$value?) (s/eq true)
   (s/optional-key :$valid?) BooleanBom
   (s/optional-key :$valid-var-path) [s/Any]
   (s/optional-key :$constraints) {base/ConstraintName s/Any}
   (s/optional-key :$guards) [s/Any]
   (s/optional-key :$refinements) {SpecId (s/conditional
                                           is-concrete-instance-bom? (s/recursive #'ConcreteInstanceBom)
                                           is-no-value-bom? NoValueBom
                                           :else ContradictionBom)}})

(def ConcreteInstanceBom
  "Indicates that a value in a bom must be and instance of a given spec."
  (assoc BareInstanceBom
         :$instance-of SpecId
         (s/optional-key :$enum) #{InstanceValue}
         (s/optional-key :$value?) BooleanBom
         (s/optional-key :$valid?) BooleanBom
         (s/optional-key :$extrinsic?) (s/eq true)
         (s/optional-key :$refinements) {SpecId (s/conditional
                                                 is-concrete-instance-bom? (s/recursive #'ConcreteInstanceBom)
                                                 is-no-value-bom? NoValueBom
                                                 :else ContradictionBom)}
         (s/optional-key :$constraints) {base/ConstraintName s/Any}
         (s/optional-key :$instance-literals) {String InstanceLiteralBom}
         (s/optional-key :$valid-vars) {String s/Any}))

(def AbstractInstanceBom
  "Indicates that a value in a bom must be an instance that can be refined to the given spec."
  (-> BareInstanceBom
      (assoc :$refines-to SpecId
             (s/optional-key :$value?) BooleanBom
             (s/optional-key :$refinements) {SpecId (s/conditional
                                                     is-concrete-instance-bom? (s/recursive #'ConcreteInstanceBom)
                                                     is-no-value-bom? NoValueBom
                                                     :else ContradictionBom)}
             (s/optional-key :$concrete-choices) {SpecId (s/conditional
                                                          is-concrete-instance-bom? ConcreteInstanceBom
                                                          :else InstanceValue)})))

(def InstanceBom
  "Umbrella type for all of the valid bom objects that can appear as the value for a spec instance."
  (s/conditional
   is-abstract-instance-bom? AbstractInstanceBom
   is-concrete-instance-bom? ConcreteInstanceBom
   is-instance-literal-bom? InstanceLiteralBom
   is-contradiction-bom? ContradictionBom))

(def InstanceBomOrValue
  "Includes both InstanceBom and InstanceValue"
  (s/conditional
   is-abstract-instance-bom? AbstractInstanceBom
   is-concrete-instance-bom? ConcreteInstanceBom
   is-instance-literal-bom? InstanceLiteralBom
   is-contradiction-bom? ContradictionBom
   :else InstanceValue))

;;

(def meta-fields
  "The system defined field names that are included in instances and instance boms. Note: these are
  not all of the fields used in any bom object, but rather are the field name used in instance boms
  or instance values."
  #{:$type

    :$instance-of
    :$refines-to
    :$instance-literal-type

    :$refinements
    :$concrete-choices
    :$instance-literals

    :$value?
    :$enum

    :$guards
    :$valid?
    :$valid-vars
    :$valid-var-path
    ;; :$extrinsic?

    :$constraints})

(s/defn to-bare-instance-bom :- BareInstanceBom
  "Take all meta-fields out of instance."
  [instance :- InstanceBomOrValue]
  (apply dissoc instance meta-fields))

(s/defn strip-bare-fields
  "Remove all but the meta fields"
  [instance :- InstanceBomOrValue]
  (select-keys instance meta-fields))

;;

(s/defn get-spec-id :- SpecId
  "Single function to retrieve the spec-id from a bom object or value that represents an instance,
  regardless of what kind of object it is."
  [bom :- InstanceBomOrValue]
  (or (:$refines-to bom)
      (:$instance-of bom)
      (:$instance-literal-type bom)
      (:$type bom)))

(s/defn instance-bom-halite-type :- types/HaliteType
  "Produce the halite type that corresponds to this bom object or value."
  [bom :- InstanceBomOrValue]
  ((cond
     (is-concrete-instance-bom? bom) types/concrete-spec-type
     (is-abstract-instance-bom? bom) types/abstract-spec-type
     (is-instance-value? bom) types/concrete-spec-type
     :default (throw (ex-info "unknown bom type" {:bom bom})))
   (get-spec-id bom)))
