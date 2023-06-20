;; Copyright (c) 2022,2023 Viasat, Inc.
;; Licensed under the MIT license

(ns com.viasat.halite.bom-user-tutorial
  (:require [clojure.test :refer :all]
            [com.viasat.halite.bom-user :as bom-user]
            [schema.core :as s]
            [schema.test]))

(set! *warn-on-reflection* true)

(defmacro example-bom
  "This is a macro rather than a function so we get better error lines when assertions fail."
  [bom & args]
  `(is (nil? (s/check bom-user/UserBom ~bom))))

(defmacro not-a-bom
  [bom & args]
  `(is (not (nil? (s/check bom-user/UserBom ~bom)))))

(deftest primitives
  "Boms are used to describe alternate values.

Resource specifications define the rules for how to construct valid resource instances. They
effectively define a universe of possible resource instances. Boms are used to talk about subsets of
that universe of instances.

Instances are composed of primitive parts so boms must also allow us to talk about primitives.

Primitive values include booleans, strings, integers, and fixed decimals.  Values of these types are
valid boms"

  (example-bom true)

  (example-bom "hello")

  (example-bom 1)

  (example-bom #d "1.1")

  (example-bom #d "1.23456")

  "Bom values can also be vectors or sets recursively composed of primitives. For example:"

  (example-bom [1 2 3])

  (example-bom #{"hello" "goodbye"})

  (example-bom [#{"hello" "goodbye"} #{"done"}])

  "Vectors and sets must contain homogenous values. They cannot contain values of different types,
  e.g. they cannot contain both strings and integers."

  "Other primitives, which may be valid in Clojure, but are not specifically supported as Bom values
  are not boms.
"
  (not-a-bom :my-key)

  "Maps in general are not boms. However, we will see later that under specific conditions maps play
  a central role in defining instances."

  (not-a-bom {:a 1}))

(deftest enums
  "Boms can enumerate a set to limit the allowed values."

  (example-bom {:$enum #{1 3 6}})

  (example-bom {:$enum #{"large" "medium" "small"}})

  (example-bom {:$enum #{#d "1.1" #d "1.0"}})

  (example-bom {:$enum #{true}}))

(deftest ranges
  "The $ranges field is used to limit numeric values based on ranges. Each range is specified as a
  pair of number. The first number is included in the range, the second number is excluded."
  (example-bom {:$ranges #{[0 10]}})
  "Multiple ranges can be included. This means the value must be in one of the ranges."
  (example-bom {:$ranges #{[0 10] [20 30]}})
  "It is not possible to indicate an unbounded range (i.e. it must have a pair of numbers, one for the lower and upper bounds)."

  "Ranges works with fixed decimal fields."
  (example-bom {:$ranges #{[#d "0.01" #d "10.00"]}})

  "Ranges work in combination with $enum values, although the usefullness is dubious"
  (example-bom {:$enum #{1 2 3}
                :$ranges #{[0 10]}}))

(deftest instances
  "Resource instances are the central data values of interest. Resource instances are a degenerate
  kind of bom. Specifically, they are a bom that does not have any degrees of freedom."

  (example-bom {:$type :test/A$v1
                :x 1
                :y 2})

  "Resource instances can be composite values, i.e. they can contain other resource instances."

  (example-bom {:$type :test/A$v1
                :x 1
                :b {:$type :test/B$v1
                    :y 2}})

  "Resource instances can also be composits of the other types, e.g. collections and primitives."

  (example-bom {:$type :test/A$v1
                :x 1
                :numbers [1 2 3]
                :b {:$type :test/B$v1
                    :y 2
                    :sizes #{"large" "small"}
                    :complete true}})

  "Instances must have a specification as their type."

  (not-a-bom {:$type "Boolean"}))

(deftest instance-boms
  "A bom can also describe instances, without fully specifying them."

  "Instance boms are a way of describing degrees of freedom in instance values. Instance boms are
maps which have a special key of either $:refines-to or :$instance-of."

  (example-bom {:$instance-of :test/A$v1} "Refers to all of the resource instances which are of type :test/A$v1")

  "If an instance bom does not include a field from the corresponding resource specification then
the bom is placing no additional constraint on the values that field can take on.

To express additional constraints on fields, those fields are included in the instance bom."

  (example-bom {:$instance-of :test/A$v1
                :x 100}
               "Refers to all of the resource instances which are of type :test/A$v1 and which have an x value of 100")

  "The values of fields in instance boms are boms."

  (example-bom {:$instance-of :test/A$v1
                :x {:$enum #{100 200}}}
               "Refers to all instances of A where x is either 100 or 200")

  "Instance boms can be nested and can include instance values (NOTE: 'instance values' and
'instance boms' are two different things)."
  (example-bom {:$instance-of :test/A$v1
                :x {:$enum #{100 200}}
                :b {:$instance-of :test/B$v1
                    :y #{1 2}}
                :c {:$type :test/C$v1
                    :p 99}})

  "Instance boms must indicate specification ids as their type. The following are not valid."

  (not-a-bom {:$instance-of "String"})

  (not-a-bom {:$instance-of ["String"]}))

(deftest instance-refinement-boms
  "Boms can also describe things that must be true about the refinements of an instance."

  (example-bom {:$instance-of :test/A$v1
                :$refinements {:ws/B$v1 {:$instance-of :ws/B$v1
                                         :y 2}}}
               "Refers to all instances of A which refine to an instance of B with a value of y=2.")

  "Refinements can be indicated recursively."
  (example-bom {:$instance-of :test/A$v1
                :$refinements {:ws/B$v1 {:$instance-of :ws/C$v1
                                         :$refinements {:ws/C$v1 {:$instance-of :ws/C$v1
                                                                  :z 3}}}}})

  "Refinements cannot indicate instances"
  (not-a-bom {:$instance-of :test/A$v1
              :$refinements {:ws/B$v1 {:$type :ws/B$v1
                                       :y 2}}}))

(deftest instance-enums
  "Similar to primitive boms, instance boms can enumerate a set to limit the allowed values."
  (example-bom {:$instance-of :test/A$v1
                :$enum #{{:$type :test/A$v1
                          :x 1}
                         {:$type :test/A$v1
                          :x 2}}}))

(deftest abstract-instance-boms
  "The example boms so far have used the :$instance-of field to indicate the resource
specification that they are instances of. Alternatively, the :$refines-to field can be used to
indicate that any resource specification that can be refined into the given type is acceptable."

  (example-bom {:$refines-to :test/A$v1} "Refers to all of the resource instance which either are :test/A$v1 instances or which can be refined into a :test/A$v1 instance.")

  "Fields and constraint expressions can also be used on abstract instance boms"

  (example-bom {:$refines-to :test/A$v1
                :x 100})

  "Compositions are also allowed in abstract instance boms."

  (example-bom {:$refines-to :test/A$v1
                :x 100
                :b {:$instance-of :test/A$v1}})

  "As with concrete instance boms, the type must be a specification."

  (not-a-bom {:$refines-to [:test/A$v1]})

  (not-a-bom {:$refines-to "Integer"}))

(deftest abstract-instance-bom-choices
  "When dealing with an abstract instance bom, the possible concrete values can be enumerated. When
  the :$concrete-choices field is present, the instance (if present) must have a spec-id which
  corresponds to one of the keys of the :$concrete-choices map."
  (example-bom {:$refines-to :ws/A$v1
                :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}
                                    :ws/C$v1 {:$instance-of :ws/C$v1}}}))

(deftest optional-fields
  "Boms can also deal with resource specifications which include optional fields.

In the following examples, all of the fields are assumed to be defined as optional in the resource
specifications.

Considering optional fields shows that the :$enum field means 'if this field has a value, then the
value must be from this set'. Specifically the presence of an :$enum field does not mean the field
must have a value. "

  (example-bom {:$instance-of :test/A$v1
                :x {:$enum #{1 2}}}
               "Means that if x is provided it must be 1 or 2, but x does not have to be provided to match this bom.")

  "When dealing with an optional field, the :$value? field is used to indicate whether the field must
  have a value to satisfiy the bom."

  (example-bom {:$instance-of :test/A$v1
                :x {:$value? true
                    :$enum #{1 2}}}
               "Means that x must be provided and it must be 1 or 2.")

  (example-bom {:$instance-of :test/A$v1
                :x {:$value? false
                    :$enum #{1 2}}}
               "It is valid to say that a field must be omitted while also indicating an
               enumeration, but in this case the enumeration has no effect on the meaning of the
               bom.")

  "Ranges are interpreted in the same way in the presence of optional fields."
  (example-bom {:$instance-of :test/A$v1
                :x {:$ranges #{[1 10]}}}
               "Means that if x is provided it must be in the range, but x does not have to be provided to match this bom.")

  "Ranges are interpreted in the same way in the presence of optional fields."
  (example-bom {:$instance-of :test/A$v1
                :x {:$value? true
                    :$ranges #{[1 10]}}}
               "Means that x must be provided and it must be in the range.")

  (example-bom {:$instance-of :test/A$v1
                :x {:$value? false
                    :$ranges #{[1 10]}}}
               "It is valid to say that a field must be omitted while also indicating ranges but in
               this case the ranges have no effect on the meaning of the bom.")

  "An instance bom can indicate that a field must be provided without specifying any other
  constraints on the value."

  (example-bom {:$instance-of :test/A$v1
                :x {:$value? true}}
               "Only matches resource instances that define a value for the optional field, x")

  "Similarly an instance bom can indicate that an optional field must be omitted from the matching
resource instances."
  (example-bom {:$instance-of :test/A$v1
                :x {:$value? false}}
               "Only matches resource instances that do not define a value for the optional field, x")

  "It is important to understand the subtle difference between
instance values and instance boms.

Assume that the :test/A$v1 resource specification calls for an
optional field named x."

  (example-bom {:$type :test/A$v1}
               "Refers to the instances of A where x is not defined. Omitting a field in an instance value means that field is omitted.")

  (example-bom {:$instance-of :test/A$v1
                :x {:$value? false}}
               "Also refers to the instances of A where x is not defined.")

  (example-bom {:$instance-of :test/A$v1}
               "Refers to all instances of A. Ommitting a field in an instance bom means there is no additional constraint on the field.")

  "Abstract instance boms can also deal with optionality."
  (example-bom {:$refines-to :ws/A$v1
                :$value? true
                :$concrete-choices {:ws/B$v1 {:$instance-of :ws/B$v1}
                                    :ws/C$v1 {:$instance-of :ws/C$v1}}})

  "Refinements are another form of optionality."
  (example-bom {:$instance-of :test/A$v1
                :$refinements {:ws/B$v1 {:$value? false}}}
               "Refers to all instances of A which do not refine to B."))

(deftest contradiction
  "The following is a bom indicating that constraints cannot be satisfied."
  (example-bom {:$contradiction? true}))

;; (run-tests)
