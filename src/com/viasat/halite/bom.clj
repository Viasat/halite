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

(defn is-instance-literal-bom?
  [x]
  (and (map? x)
       (:$instance-literal-type x)))

(defn is-instance-bom?
  [x]
  (or (is-abstract-instance-bom? x)
      (is-concrete-instance-bom? x)
      (is-instance-literal-bom? x)))

(def ExpressionBom {:$expr s/Any})

(defn is-expression-bom? [x]
  (and (map? x)
       (contains? x :$expr)))

(defn is-primitive-bom?
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

(def BomValue (s/conditional
               base/integer-or-long? Number
               string? String
               boolean? Boolean
               base/fixed-decimal? FixedDecimal
               is-instance-value? (s/recursive #'InstanceValue)
               set? #{(s/recursive #'BomValue)}
               vector? [(s/recursive #'BomValue)]))

(defn is-primitive-value? [x]
  (or
   (base/integer-or-long? x)
   (string? x)
   (boolean? x)
   (base/fixed-decimal? x)
   (is-instance-value? x)
   (and (set? x)
        (every? is-primitive-value? x))
   (and (vector? x)
        (every? is-primitive-value? x))))

(defn is-bom-value? [x]
  (nil? (s/check BomValue x)))

(def InstanceValue
  {:$type SpecId
   VariableKeyword BomValue})

(declare AbstractInstanceBom)

(declare ConcreteInstanceBom)

(def BooleanBom (s/conditional
                 map? {(s/optional-key :$primitive-type) types/HaliteType
                       (s/optional-key :$enum) #{Boolean}}
                 :else Boolean))

(def IntegerRangeConstraint [(s/one Number :start) (s/one Number :end)])

(def FixedDecimalRangeConstraint [(s/one FixedDecimal :start) (s/one FixedDecimal :end)])

(def RangeConstraint (s/conditional
                      #(base/integer-or-long? (first %)) IntegerRangeConstraint
                      :else FixedDecimalRangeConstraint))

;; ranges are interpreted to include the lower bound and exclude the upper bound
;; it is not possible to specify an 'open ended' range

(def RangesConstraint #{RangeConstraint})

(def PrimitiveBom
  {(s/optional-key :$ranges) RangesConstraint
   (s/optional-key :$enum) #{BomValue}
   (s/optional-key :$value?) BooleanBom
   (s/optional-key :$primitive-type) types/HaliteType})

(declare AbstractInstanceBom)

(declare ConcreteInstanceBom)

(declare InstanceLiteralBom)

(def Bom (s/conditional
          is-abstract-instance-bom? (s/recursive #'AbstractInstanceBom)
          is-concrete-instance-bom? (s/recursive #'ConcreteInstanceBom)
          is-instance-literal-bom? (s/recursive #'InstanceLiteralBom)
          is-primitive-bom? PrimitiveBom
          is-expression-bom? ExpressionBom
          :else BomValue))

(def NoValueBom {:$value? (s/eq false)})

(def no-value-bom {:$value? false})

(s/defn is-no-value-bom? [bom]
  (= no-value-bom bom))

(s/defn is-a-no-value-bom?
  "This handles concrete instance boms which also have a field indicating type."
  [bom]
  (= false (:$value? bom)))

(def YesValueBom {:$value? (s/eq true)})

(def yes-value-bom {:$value? true})

(s/defn is-yes-value-bom? [bom]
  (= yes-value-bom bom))

(def ContradictionBom {:$contradiction? (s/eq true)})

(def contradiction-bom {:$contradiction? true})

(s/defn is-contradiction-bom? [bom]
  (= contradiction-bom bom))

(def VariableValueBom (s/conditional
                       is-instance-value? InstanceValue
                       is-no-value-bom? NoValueBom
                       is-contradiction-bom? ContradictionBom
                       :else Bom))

(def BareInstanceBom {VariableKeyword VariableValueBom})

(def InstanceLiteralBom
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

(def InstanceBom (s/conditional
                  is-abstract-instance-bom? AbstractInstanceBom
                  is-concrete-instance-bom? ConcreteInstanceBom
                  is-instance-literal-bom? InstanceLiteralBom
                  is-contradiction-bom? ContradictionBom))

(def InstanceBomOrValue (s/conditional
                         is-abstract-instance-bom? AbstractInstanceBom
                         is-concrete-instance-bom? ConcreteInstanceBom
                         is-instance-literal-bom? InstanceLiteralBom
                         :else InstanceValue))

;;

(def TypeField (s/enum :$refines-to :$instance-of))

(def meta-fields #{:$refines-to
                   :$instance-of
                   :$instance-literal-type
                   :$guards
                   :$enum
                   :$value?
                   :$valid?
                   :$valid-vars
                   :$valid-var-path
                   :$extrinsic?
                   :$refinements
                   :$concrete-choices
                   :$instance-literals
                   :$expr
                   :$type
                   :$primitive-type
                   :$constraints
                   :$id-path})

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
  (let [result (apply dissoc instance meta-fields)]
    (when (s/check BareInstanceBom result)
      (clojure.pprint/pprint [:tbib :instance instance :result (apply dissoc instance meta-fields)])))
  (apply dissoc instance meta-fields))

(s/defn strip-bare-fields
  "Remove all but the meta fields"
  [instance :- InstanceBomOrValue]
  (select-keys instance meta-fields))

;;

(s/defn get-spec-id :- SpecId
  [bom :- InstanceBomOrValue]
  (or (:$refines-to bom)
      (:$instance-of bom)
      (:$instance-literal-type bom)
      (:$type bom)))

(s/defn instance-bom-halite-type :- types/HaliteType
  [bom :- InstanceBomOrValue]
  ((cond
     (is-concrete-instance-bom? bom) types/concrete-spec-type
     (is-abstract-instance-bom? bom) types/abstract-spec-type
     (is-instance-value? bom) types/concrete-spec-type
     :default (throw (ex-info "unknown bom type" {:bom bom})))
   (get-spec-id bom)))

;;

(def PrimitiveType (apply s/enum types/primitive-types))

(def VariableType (s/conditional
                   string? PrimitiveType
                   vector? (s/constrained [(s/recursive #'VariableType)]
                                          #(pos? (count %)))
                   set? (s/constrained #{(s/recursive #'VariableType)}
                                       #(pos? (count %)))
                   :else SpecId))
