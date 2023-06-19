;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom
  "Internal schema definitions for the Bom structure that describes instance values."
  (:require [com.viasat.halite.base :as base]
            [com.viasat.halite.bom-user :as bom-user]
            [com.viasat.halite.types :as types]
            [potemkin]
            [schema.core :as s])
  (:import [clojure.lang IObj]
           [com.viasat.halite.lib.fixed_decimal FixedDecimal]))

(set! *warn-on-reflection* true)

(potemkin/import-vars
 [bom-user
  is-abstract-instance-bom?
  is-concrete-instance-bom?
  is-instance-value?
  BomValue
  RangesConstraint
  NoValueBom
  no-value-bom
  is-no-value-bom?
  ContradictionBom
  contradiction-bom
  is-contradiction-bom?
  InstanceValue
  VariableKeyword
  SpecId])

;;;;

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

(defn is-bom-value?
  "See BomValue"
  [x]
  (nil? (s/check BomValue x)))

(declare AbstractInstanceBom)

(declare ConcreteInstanceBom)

(def BooleanBom
  "A bom object used to describe a boolean field."
  (s/conditional
   map? {(s/optional-key :$primitive-type) types/HaliteType
         (s/optional-key :$enum) #{Boolean}}
   :else Boolean))

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
   is-contradiction-bom? ContradictionBom
   is-abstract-instance-bom? (s/recursive #'AbstractInstanceBom)
   is-concrete-instance-bom? (s/recursive #'ConcreteInstanceBom)
   is-instance-literal-bom? (s/recursive #'InstanceLiteralBom)
   is-primitive-bom? PrimitiveBom
   is-expression-bom? ExpressionBom
   :else BomValue))

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
  "An internal representation used to bring instance literals from spec expression into the bom directly as
  objects (i.e. outside of their containing expressions)."
  {VariableKeyword (s/conditional
                    is-expression-bom? ExpressionBom
                    :else VariableValueBom)
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
                                                     is-concrete-instance-bom? ConcreteInstanceBom
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

;;;;

(defprotocol LoweredObject
  (is-lowered-object? [this]))

(extend-type Object
  LoweredObject
  (is-lowered-object? [_] false))

(defn flagged-lowered?
  "Returns true if is flagged, returns false if it definitely is not, returns nil if the expr is not
  able to be flagged and therefore the answer is unknown."
  [expr]
  (cond
    (is-lowered-object? expr) true
    (instance? IObj expr) (= true (:lowered? (meta expr)))
    :default nil))

(defn- flag-lowered*
  [expr]
  (cond
    (is-lowered-object? expr) expr
    (instance? clojure.lang.IObj expr) (with-meta expr (assoc (meta expr)
                                                              :lowered? true))
    :default expr))

(defn flag-lowered
  [expr]
  (when (and (not (is-lowered-object? expr))
             (= true (flagged-lowered? expr)))
    (throw (ex-info "did not expect flag-lowered to be called" {:expr expr
                                                                :meta (meta expr)})))
  (flag-lowered* expr))

(defn ensure-flag-lowered
  [expr]
  (if (flagged-lowered? expr)
    expr
    (flag-lowered* expr)))

(def Expr (s/pred #(not (= true (flagged-lowered? %)))))

(def LoweredExpr (s/pred #(let [fl (flagged-lowered? %)]
                            (or (= true fl)
                                (nil? fl)))))
